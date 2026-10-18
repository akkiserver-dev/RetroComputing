package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.hardware.Hardware;
import jp.akkiserver.rc.api.hardware.HardwareInfo;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.Unsigned;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModuleTest {
    private static class BaseHardware implements Hardware {
        private final UUID address = UUID.randomUUID();

        @Override
        public final HardwareInfo info() {
            return () -> address;
        }
    }

    @Module
    public static class LabeledHardware extends BaseHardware {
        protected String label = "";

        @Export
        public String getLabel() {
            return label;
        }

        @Export
        public void setLabel(String label) {
            this.label = label;
        }

        @Export
        public @Unsigned int getLength() {
            return label.length();
        }
    }

    @Module
    public static class LabeledExtendedHardware extends LabeledHardware {
        @Export
        public void clearLabel() {
            this.label = "";
        }

        @Export
        public void appendLabel(String label) {
            this.label = this.label + label;
        }
    }

    @Module
    public static class PrivateExportHardware extends BaseHardware {
        @Export
        private void privateExport() {

        }
    }

    @Module
    public static class StaticExportHardware extends BaseHardware {
        @Export
        public static void staticExport() {

        }
    }

    @Module
    public static class InvalidMethodArgumentHardware extends BaseHardware {
        @Export
        public void invalidMethod(@Unsigned String unsignedString) {

        }
    }

    public static class UnmarkedHardware extends BaseHardware {
        @Export
        public int value() {
            return 1;
        }
    }

    public static class UnmarkedChild extends LabeledHardware {
    }

    @Module
    public static class EmptyModule {
    }

    @Module
    public static class UnexportedOverride extends LabeledHardware {
        @Override
        public String getLabel() {
            return label;
        }
    }

    @Module
    public static class AsyncOverride extends LabeledHardware {
        @Override
        @Export(async = true)
        public String getLabel() {
            return label;
        }
    }

    private static class InvalidBase extends BaseHardware {
        @Export
        private void hiddenExport() {
        }
    }

    @Module
    public static class InvalidBaseChild extends InvalidBase {
    }

    @Module
    public static class OverloadedModule {
        @Export
        public void read(int value) {
        }

        @Export
        public void read(String value) {
        }
    }

    @Module
    public static class InvalidNameModule {
        @Export
        public void $invalid() {
        }
    }

    @Module
    public static class UnsupportedTypeModule {
        @Export
        public void accept(Object value) {
        }
    }

    @Module
    public static class RegistrationOnlyModule {
        @Export
        public int value() {
            throw new AssertionError("Registration must not execute exported methods");
        }
    }

    @Test
    void registersMethodsInNameOrderWithInferredTypes() {
        assertEquals(List.of(
                new MethodInfo(0, "getLabel", Signature.parse("-> string"), false),
                new MethodInfo(1, "getLength", Signature.parse("-> u32"), false),
                new MethodInfo(2, "setLabel", Signature.parse("string -> void"), false)
        ), MethodTable.create(new LabeledHardware()).methods());
    }

    @Test
    void includesInheritedExportsAndReassignsIdsInNameOrder() {
        assertEquals(List.of(
                new MethodInfo(0, "appendLabel", Signature.parse("string -> void"), false),
                new MethodInfo(1, "clearLabel", Signature.parse("-> void"), false),
                new MethodInfo(2, "getLabel", Signature.parse("-> string"), false),
                new MethodInfo(3, "getLength", Signature.parse("-> u32"), false),
                new MethodInfo(4, "setLabel", Signature.parse("string -> void"), false)
        ), MethodTable.create(new LabeledExtendedHardware()).methods());
    }

    @Test
    void requiresModuleEvenWhenMethodsAreExported() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new UnmarkedHardware()));
    }

    @Test
    void requiresModuleOnTheConcreteSubclass() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new UnmarkedChild()));
    }

    @Test
    void acceptsAnEmptyModuleWithoutHardware() {
        assertTrue(MethodTable.create(new EmptyModule()).methods().isEmpty());
    }

    @Test
    void excludesAnOverrideWithoutExport() {
        assertEquals(List.of(
                new MethodInfo(0, "getLength", Signature.parse("-> u32"), false),
                new MethodInfo(1, "setLabel", Signature.parse("string -> void"), false)
        ), MethodTable.create(new UnexportedOverride()).methods());
    }

    @Test
    void usesTheExportAnnotationOnTheOverride() {
        assertEquals(List.of(
                new MethodInfo(0, "getLabel", Signature.parse("-> string"), true),
                new MethodInfo(1, "getLength", Signature.parse("-> u32"), false),
                new MethodInfo(2, "setLabel", Signature.parse("string -> void"), false)
        ), MethodTable.create(new AsyncOverride()).methods());
    }

    @Test
    void rejectsPrivateExports() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new PrivateExportHardware()));
    }

    @Test
    void rejectsStaticExports() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new StaticExportHardware()));
    }

    @Test
    void validatesExportsDeclaredInBaseClasses() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new InvalidBaseChild()));
    }

    @Test
    void rejectsOverloadedExportNames() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new OverloadedModule()));
    }

    @Test
    void rejectsNamesOutsideTheAbiIdentifierFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new InvalidNameModule()));
    }

    @Test
    void rejectsUnsignedOnAStringParameter() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new InvalidMethodArgumentHardware()));
    }

    @Test
    void rejectsUnsupportedParameterTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodTable.create(new UnsupportedTypeModule()));
    }

    @Test
    void exposesAnUnmodifiableMethodList() {
        List<MethodInfo> methods = MethodTable.create(new LabeledHardware()).methods();
        assertThrows(UnsupportedOperationException.class, methods::clear);
    }

    @Test
    void registrationDoesNotExecuteExportedMethods() {
        assertDoesNotThrow(() -> MethodTable.create(new RegistrationOnlyModule()));
    }
}
