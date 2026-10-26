# RC Shuttle ゲストABI 0.1

RCのJavaファームウェア実装に対応するC／Rust用定義。
Shuttleの汎用ランタイムAPIとは別の、ゲスト側のインターフェースです。

- C/C++: `include/rc_abi.h`
- Rust: `rust/`（`rc-shuttle-abi`、`no_std`、外部依存なし）
- 実装元: `../architecture-shuttle/src/main/java/jp/akkiserver/rc/architecture/shuttle/ShuttleArchitecture.java`

## C

```c
#include "rc_abi.h"
RC_ABI_DECLARE(); /* 最終Wasmに1回だけ */

void _start(void) {
    const char text[] = "Hello from C";
    rc_fw_log(text, sizeof(text) - 1);
}
```

```sh
clang --target=wasm32 -std=c11 -ffreestanding -nostdlib \
  -Iabi/include hello.c \
  -Wl,--no-entry,--export=_start,--export-memory -o hello.wasm
```

C++からは`_start`を`extern "C"`で定義してください。
ヘッダ内のインポートにはCリンケージが設定されています。

## Rust

`Cargo.toml`に依存を追加します。`path`はそのファイルからの相対パスです。

```toml
[dependencies]
rc-shuttle-abi = { path = "../../../abi/rust" }
```

```rust
#![no_std]
use core::panic::PanicInfo;
use rc_shuttle_abi::fw;

rc_shuttle_abi::declare_abi!(); // 最終Wasmに1回だけ

#[unsafe(no_mangle)]
pub extern "C" fn _start() {
    unsafe { fw::log(b"Hello from Rust".as_ptr(), 15); }
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    core::arch::wasm32::unreachable()
}
```

`crate-type = ["cdylib"]`、`panic = "abort"`を指定し、
`cargo build --target wasm32-unknown-unknown --release`でビルドします。
テストハーネスを使わないゲストでは`[lib]`の`test`／`doctest`を`false`にします。
動作する構成例は`../guests/examples/screen/`にあります。

## 呼び出し規約

- インポートはトップレベルのBIOS／OS用の`fw`・`co`と、子ゲスト専用の`sys.call`に分かれます。子ゲストから`fw`・`co`は使えません。
- `fw.yield`はCでは`rc_fw_yield_now`、Rustでは`fw::yield_now`です。
- `co.co_create`などはCでは`rc_co_create`、Rustでは`co::create`です。
- `_start() -> void`と`memory`をエクスポートします。Wasmのstartセクションは使いません。
- 各ゲストに`rc.abi`カスタムセクションを1つ付けます。内容はmajor=0、minor=1のu32 LEです。C／Rustの宣言マクロで生成できます。
- ポインタは呼び出し元のWasmメモリ内のアドレスです。長さ・容量は0以上のi32です。
- 出力APIは必要バイト数または負のエラー番号を返します。容量0で必要量を取得でき、容量不足なら出力は書き換えません。文字列はNULなしのUTF-8です。
- `dev_call`は容量0でも実行します。容量不足時は`dev_result(HELD_RESULT, ...)`で結果を取得してください。同じ`dev_call`
  の再実行は副作用を繰り返します。保持結果は次の`dev_call`で破棄されます。
- `dev_result`は未完了なら`E_AGAIN`。結果を全て読み出すか終端エラーを受け取るとチケットを消費します。容量不足では保持します。
- `poll_event`は空なら0。容量不足なら必要量を返し、イベントを保持します。
- `hlt(0)`はイベントまで待機し、正の引数はtick数です。イベントで復帰すると1、時間切れでは0を返します。
- `exec`の成功、`shutdown`、`reboot`には通常の継続がありません。`exec`のエラーは負の値で戻ります。
- `sys.call`の番号の意味はOSが定義します。親は`co::resume`の`syscall_result`で戻り値を渡します。
- 子ゲストの予約メモリには、Wasmの初期メモリに加えてランタイムの管理領域が必要です。

## バイト列

整数・浮動小数点はリトルエンディアン。UUIDの16バイトはRFCの並びです。
構造体のサイズはCとRustのコンパイル時に検査しています。

| データ                    | 内容                                                                            |
|---------------------------|---------------------------------------------------------------------------------|
| DeviceInfo（24バイト）    | class:u16, vendor:u16, device:u16, reserved:u16, uuid:16バイト                  |
| EventHeader（20バイト）   | kind:u16, slot:u16, parameter:i32, tick:u64, payload_len:u32。その直後にpayload |
| ChildInfo（48バイト）     | reason:i32, number:i32, arguments:i64×4, budget_used:i64                        |
| KeyboardSignal（8バイト） | kind:u8, modifiers:u8, GLFW key:u16, Unicode code_point:u32                     |
| DescribeHeader（4バイト） | version:u8=1, reserved:u8=0, method_count:u16                                   |

`dev_describe`ではヘッダに続いて、メソッド数分の次のエントリが並びます。

```text
id:u16, async:u8, name_len:u8, name:ASCII[name_len],
argc:u8, argument_type:u8[argc], return_type:u8
```

メソッドIDは登録済みの名前順で固定されますが、API追加で変わり得るため名前から解決します。
引数・戻り値は型コードに従って連続配置します。文字列はu32のUTF-8バイト長＋本文、
配列はu32の要素数＋要素列。配列型は`WIRE_ARRAY_FLAG`をORします。boolは1バイトの0／1、voidは0バイトです。
null表現はありません。Rustの`bool`や`&str`、スライスをそのまま渡さず、ABIのバイト列に変換してください。

Rustの`EventHeader::decode`／`payload`は、非整列のバッファと長さ不足を扱えます。
構造体を直接参照する場合、packedフィールドへの参照を作らないようにしてください。

## 検証

```powershell
# Rustの単体テスト、C/C++ヘッダ検査、C/Rustの親・子ゲストをビルド
.\abi\test.ps1 -Clang "C:/Program Files/LLVM/bin/clang.exe"

# 全インポートの型、子ゲストのsys.callと復帰値、定数とJava実装の一致を検査
.\gradlew.bat :architecture-shuttle:test
```

ClangとRustの`wasm32-unknown-unknown`ターゲットが必要です。
ゲストのビルド成果物がない環境ではネイティブとのC/Rust連携テストだけスキップします。
