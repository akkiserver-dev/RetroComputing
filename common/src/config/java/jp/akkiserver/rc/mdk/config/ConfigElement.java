package jp.akkiserver.rc.mdk.config;

interface ConfigElement {
    void bindTo(ConfigVisitor visitor);
}
