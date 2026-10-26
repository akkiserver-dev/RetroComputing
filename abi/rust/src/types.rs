//! Fixed-layout structures. Prefer decoding byte buffers over pointer casts.

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct DeviceInfo {
    pub class: u16,
    pub vendor: u16,
    pub device: u16,
    pub reserved: u16,
    pub uuid: [u8; 16],
}

/// The payload immediately follows these 20 bytes. Copy packed fields to a
/// local variable before taking references to them (especially `tick`).
#[repr(C, packed)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct EventHeader {
    pub kind: u16,
    pub slot: u16,
    pub parameter: i32,
    pub tick: u64,
    pub payload_len: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct ChildInfo {
    pub reason: i32,
    pub number: i32,
    pub arguments: [i64; 4],
    pub budget_used: i64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct DescribeHeader {
    pub version: u8,
    pub reserved: u8,
    pub method_count: u16,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct KeyboardSignal {
    pub kind: u8,
    pub modifiers: u8,
    pub key: u16,
    pub code_point: u32,
}

const _: () = assert!(core::mem::size_of::<DeviceInfo>() == 24);
const _: () = assert!(core::mem::size_of::<EventHeader>() == 20);
const _: () = assert!(core::mem::size_of::<ChildInfo>() == 48);
const _: () = assert!(core::mem::size_of::<DescribeHeader>() == 4);
const _: () = assert!(core::mem::size_of::<KeyboardSignal>() == 8);

impl EventHeader {
    pub const SIZE: usize = 20;

    pub fn decode(bytes: &[u8]) -> Option<Self> {
        Some(Self {
            kind: u16::from_le_bytes(bytes.get(0..2)?.try_into().ok()?),
            slot: u16::from_le_bytes(bytes.get(2..4)?.try_into().ok()?),
            parameter: i32::from_le_bytes(bytes.get(4..8)?.try_into().ok()?),
            tick: u64::from_le_bytes(bytes.get(8..16)?.try_into().ok()?),
            payload_len: u32::from_le_bytes(bytes.get(16..20)?.try_into().ok()?),
        })
    }

    /// Return the complete payload only; truncated events are rejected.
    pub fn payload<'a>(&self, bytes: &'a [u8]) -> Option<&'a [u8]> {
        let end = Self::SIZE.checked_add(self.payload_len as usize)?;
        bytes.get(Self::SIZE..end)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn event_decodes_unaligned_bytes_and_rejects_truncated_payload() {
        let mut bytes = [0u8; 29];
        let event = &mut bytes[1..];
        event[0..2].copy_from_slice(&4u16.to_le_bytes());
        event[2..4].copy_from_slice(&2u16.to_le_bytes());
        event[4..8].copy_from_slice(&(-7i32).to_le_bytes());
        event[8..16].copy_from_slice(&123456789u64.to_le_bytes());
        event[16..20].copy_from_slice(&8u32.to_le_bytes());
        event[20..].copy_from_slice(b"keyboard");
        let header = EventHeader::decode(event).unwrap();
        let (kind, slot, parameter, tick) =
            (header.kind, header.slot, header.parameter, header.tick);
        assert_eq!((kind, slot, parameter, tick), (4, 2, -7, 123456789));
        assert_eq!(header.payload(event), Some(&b"keyboard"[..]));
        assert!(header.payload(&event[..27]).is_none());
        assert!(EventHeader::decode(&event[..19]).is_none());
    }
}
