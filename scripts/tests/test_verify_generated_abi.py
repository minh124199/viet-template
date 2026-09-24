import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_").replace(".", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verifier = load_script("verify-generated-abi.py")


class TestJvmDescriptorDecoding(unittest.TestCase):
    """Tests decoding of JVM type and method descriptors into Java signatures."""

    def test_decode_primitive_types(self):
        primitives = {
            "B": "byte",
            "C": "char",
            "D": "double",
            "F": "float",
            "I": "int",
            "J": "long",
            "S": "short",
            "Z": "boolean",
            "V": "void",
        }
        for desc, expected in primitives.items():
            java_type, next_pos = verifier.decode_type_descriptor(desc)
            self.assertEqual(java_type, expected)
            self.assertEqual(next_pos, 1)

    def test_decode_object_types(self):
        desc = "Ljava/lang/String;"
        java_type, next_pos = verifier.decode_type_descriptor(desc)
        self.assertEqual(java_type, "java.lang.String")
        self.assertEqual(next_pos, len(desc))

        desc2 = "Lio/github/minh124199/viettemplate/api/RenderContext;"
        java_type2, next_pos2 = verifier.decode_type_descriptor(desc2)
        self.assertEqual(java_type2, "io.github.minh124199.viettemplate.api.RenderContext")
        self.assertEqual(next_pos2, len(desc2))

    def test_decode_array_types(self):
        cases = [
            ("[B", "byte[]", 2),
            ("[[B", "byte[][]", 3),
            ("[Ljava/lang/Object;", "java.lang.Object[]", len("[Ljava/lang/Object;")),
            ("[[Ljava/lang/String;", "java.lang.String[][]", len("[[Ljava/lang/String;")),
        ]
        for desc, expected_type, expected_pos in cases:
            java_type, next_pos = verifier.decode_type_descriptor(desc)
            self.assertEqual(java_type, expected_type)
            self.assertEqual(next_pos, expected_pos)

    def test_decode_method_descriptors(self):
        # void no-arg
        params, ret = verifier.decode_method_descriptor("()V")
        self.assertEqual(params, [])
        self.assertEqual(ret, "void")

        # TemplateId.of(String) -> TemplateId
        desc = "(Ljava/lang/String;)Lio/github/minh124199/viettemplate/api/TemplateId;"
        params, ret = verifier.decode_method_descriptor(desc)
        self.assertEqual(params, ["java.lang.String"])
        self.assertEqual(ret, "io.github.minh124199.viettemplate.api.TemplateId")

        # boolean isTruthy(Object, boolean) -> boolean
        desc = "(Ljava/lang/Object;Z)Z"
        params, ret = verifier.decode_method_descriptor(desc)
        self.assertEqual(params, ["java.lang.Object", "boolean"])
        self.assertEqual(ret, "boolean")

        # writeConst(TemplateOutput, String, byte[]) -> void
        desc = "(Lio/github/minh124199/viettemplate/api/TemplateOutput;Ljava/lang/String;[B)V"
        params, ret = verifier.decode_method_descriptor(desc)
        self.assertEqual(
            params,
            [
                "io.github.minh124199.viettemplate.api.TemplateOutput",
                "java.lang.String",
                "byte[]",
            ],
        )
        self.assertEqual(ret, "void")

    def test_malformed_type_descriptors_raise_error(self):
        with self.assertRaises(ValueError):
            verifier.decode_type_descriptor("")

        with self.assertRaises(ValueError):
            verifier.decode_type_descriptor("Lunterminated/Class")

        with self.assertRaises(ValueError):
            verifier.decode_type_descriptor("X")

        with self.assertRaises(ValueError):
            verifier.decode_type_descriptor("[")

    def test_malformed_method_descriptors_raise_error(self):
        with self.assertRaises(ValueError):
            verifier.decode_method_descriptor("Ljava/lang/String;)V")

        with self.assertRaises(ValueError):
            verifier.decode_method_descriptor("(Ljava/lang/String;")

        with self.assertRaises(ValueError):
            verifier.decode_method_descriptor("(Ljava/lang/String;)")

        with self.assertRaises(ValueError):
            verifier.decode_method_descriptor("()VI")

    def test_format_method_signature(self):
        sig = verifier.format_method_signature("void", "countLoopIteration", ["TemplateOutput"])
        self.assertEqual(sig, "void countLoopIteration(TemplateOutput)")

        sig_no_args = verifier.format_method_signature("int", "getCount", [])
        self.assertEqual(sig_no_args, "int getCount()")


class TestBaselineParsing(unittest.TestCase):
    """Tests baseline member normalization and file parsing."""

    def test_parse_member_signature_methods(self):
        raw = "public static java.lang.Object binaryOp(java.lang.Object, java.lang.Object, int, java.lang.String, int, int, int, int, io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy)"
        parsed = verifier.parse_member_signature(raw)
        self.assertEqual(parsed["kind"], "method")
        self.assertEqual(parsed["name"], "binaryOp")
        self.assertEqual(parsed["return_type"], "java.lang.Object")
        self.assertEqual(len(parsed["parameters"]), 9)
        self.assertEqual(
            parsed["name_and_params"],
            "binaryOp(java.lang.Object, java.lang.Object, int, java.lang.String, int, int, int, int, io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy)",
        )

        raw_abstract = "public abstract int getCount()"
        parsed_abstract = verifier.parse_member_signature(raw_abstract)
        self.assertEqual(parsed_abstract["name"], "getCount")
        self.assertEqual(parsed_abstract["return_type"], "int")
        self.assertEqual(parsed_abstract["parameters"], [])
        self.assertEqual(parsed_abstract["normalized"], "int getCount()")

    def test_parse_member_signature_fields(self):
        raw = "public static final java.lang.String CONST_VAL"
        parsed = verifier.parse_member_signature(raw)
        self.assertEqual(parsed["kind"], "field")
        self.assertEqual(parsed["name"], "CONST_VAL")
        self.assertEqual(parsed["type"], "java.lang.String")
        self.assertEqual(parsed["normalized"], "java.lang.String CONST_VAL")

    def test_parse_baseline_file(self):
        content = """# Comment line
TYPE com.example.Foo
  MEMBER public static java.lang.String hello(java.lang.String)
  MEMBER public int count()

TYPE com.example.Bar
"""
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tf:
            tf.write(content)
            tf.flush()
            temp_path = Path(tf.name)

        try:
            baseline = verifier.parse_baseline(temp_path)
            self.assertIn("com.example.Foo", baseline)
            self.assertIn("com.example.Bar", baseline)
            self.assertEqual(len(baseline["com.example.Foo"]), 2)
            self.assertEqual(len(baseline["com.example.Bar"]), 0)

            foo_members = baseline["com.example.Foo"]
            self.assertTrue(foo_members.matches_method("hello", ["java.lang.String"], "java.lang.String"))
            self.assertTrue(foo_members.matches_method("count", [], "int"))
            self.assertFalse(foo_members.matches_method("unknown", []))
            self.assertFalse(foo_members.matches_method("hello", ["int"]))
        finally:
            temp_path.unlink()

    def test_reconciled_runtime_baseline_contains_exact_contract(self):
        baseline_file = ROOT / "config" / "api-baseline" / "generated-template-runtime-abi.txt"
        self.assertTrue(baseline_file.exists())

        baseline = verifier.parse_baseline(baseline_file)
        self.assertEqual(len(baseline), 7, f"Expected exactly 7 types, found {len(baseline)}")

        expected_types = {
            "io.github.minh124199.viettemplate.api.CompiledTemplate",
            "io.github.minh124199.viettemplate.api.RenderContext",
            "io.github.minh124199.viettemplate.api.TemplateId",
            "io.github.minh124199.viettemplate.api.TemplateOutput",
            "io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata",
            "io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite",
            "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge",
        }
        self.assertEqual(set(baseline.keys()), expected_types)

        # Check BytecodeRuntimeBridge has 16 methods including countLoopIteration
        bridge_members = baseline["io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge"]
        self.assertEqual(len(bridge_members), 16)
        self.assertTrue(
            bridge_members.matches_method(
                "countLoopIteration",
                ["io.github.minh124199.viettemplate.api.TemplateOutput"],
                "void",
            )
        )

        # Check ForeachMetadata has 4 methods
        foreach_members = baseline["io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata"]
        self.assertEqual(len(foreach_members), 4)

        # Check RenderContext has get
        rc_members = baseline["io.github.minh124199.viettemplate.api.RenderContext"]
        self.assertEqual(len(rc_members), 1)
        self.assertTrue(rc_members.matches_method("get", ["java.lang.String"], "java.lang.Object"))

        # Check TemplateId has of
        tid_members = baseline["io.github.minh124199.viettemplate.api.TemplateId"]
        self.assertEqual(len(tid_members), 1)
        self.assertTrue(
            tid_members.matches_method(
                "of",
                ["java.lang.String"],
                "io.github.minh124199.viettemplate.api.TemplateId",
            )
        )

        # Check TemplateOutput and DynamicCallSite have 0 invoked members
        self.assertEqual(len(baseline["io.github.minh124199.viettemplate.api.TemplateOutput"]), 0)
        self.assertEqual(len(baseline["io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite"]), 0)


class TestClassificationLoading(unittest.TestCase):
    """Tests loading and correlation of public surface classifications."""

    def test_load_classification_from_temp_file(self):
        content = """# Surface classification
com.example.Foo STABLE_API
com.example.Bar STABLE_SPI
"""
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tf:
            tf.write(content)
            tf.flush()
            temp_path = Path(tf.name)

        try:
            mapping = verifier.load_classification(temp_path)
            self.assertEqual(mapping.get("com.example.Foo"), "STABLE_API")
            self.assertEqual(mapping.get("com.example.Bar"), "STABLE_SPI")
            self.assertIsNone(mapping.get("com.example.Baz"))
        finally:
            temp_path.unlink()

    def test_real_classification_covers_runtime_types(self):
        classification_file = ROOT / "config" / "api-baseline" / "public-surface-classification.txt"
        self.assertTrue(classification_file.exists())
        cmap = verifier.load_classification(classification_file)

        self.assertEqual(
            cmap.get("io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge"),
            "GENERATED_RUNTIME_ABI",
        )
        self.assertEqual(
            cmap.get("io.github.minh124199.viettemplate.api.CompiledTemplate"),
            "STABLE_API",
        )
        self.assertEqual(
            cmap.get("io.github.minh124199.viettemplate.api.RenderContext"),
            "STABLE_API",
        )
        self.assertEqual(
            cmap.get("io.github.minh124199.viettemplate.api.TemplateId"),
            "STABLE_API",
        )


class TestConstantPoolParsing(unittest.TestCase):
    """Tests constant pool method and field parsing."""

    def test_parse_constant_pool_method(self):
        raw = "io/github/minh124199/viettemplate/api/TemplateId.of:(Ljava/lang/String;)Lio/github/minh124199/viettemplate/api/TemplateId;"
        parsed = verifier.parse_constant_pool_method(raw)
        self.assertEqual(parsed["owner"], "io.github.minh124199.viettemplate.api.TemplateId")
        self.assertEqual(parsed["name"], "of")
        self.assertEqual(parsed["parameters"], ["java.lang.String"])
        self.assertEqual(parsed["return_type"], "io.github.minh124199.viettemplate.api.TemplateId")
        self.assertEqual(
            parsed["call_signature"],
            "io.github.minh124199.viettemplate.api.TemplateId.of(java.lang.String)",
        )

    def test_parse_constant_pool_field(self):
        raw = "io/github/minh124199/viettemplate/generated/T_01.TEMPLATE_ID:Ljava/lang/String;"
        parsed = verifier.parse_constant_pool_field(raw)
        self.assertEqual(parsed["owner"], "io.github.minh124199.viettemplate.generated.T_01")
        self.assertEqual(parsed["name"], "TEMPLATE_ID")
        self.assertEqual(parsed["type"], "java.lang.String")


class TestAbiValidation(unittest.TestCase):
    """Tests error detection during generated template ABI validation."""

    def setUp(self):
        baseline_file = ROOT / "config" / "api-baseline" / "generated-template-runtime-abi.txt"
        self.baseline = verifier.parse_baseline(baseline_file)

        # Baseline valid report structure reflecting real repository output
        self.valid_report = {
            "runtime_abi": {
                "viet_template_types": [
                    "io.github.minh124199.viettemplate.api.CompiledTemplate",
                    "io.github.minh124199.viettemplate.api.RenderContext",
                    "io.github.minh124199.viettemplate.api.TemplateId",
                    "io.github.minh124199.viettemplate.api.TemplateOutput",
                    "io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata",
                    "io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge",
                ],
                "viet_template_methods": [
                    "io.github.minh124199.viettemplate.api.RenderContext.get(java.lang.String)",
                    "io.github.minh124199.viettemplate.api.TemplateId.of(java.lang.String)",
                    "io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata.getCount()",
                    "io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata.getIndex()",
                    "io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata.isFirst()",
                    "io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata.isLast()",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.binaryOp(java.lang.Object, java.lang.Object, int, java.lang.String, int, int, int, int, io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.countLoopIteration(io.github.minh124199.viettemplate.api.TemplateOutput)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.createCallSite(int, java.lang.String, int, int, io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.createForeachMetadata(java.lang.Object, boolean, java.lang.Object)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.createLoopState()",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.dynamicGetIndex(io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite, java.lang.Object, java.lang.Object)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.dynamicGetProperty(io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite, java.lang.Object)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.dynamicInvokeMethod(io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite, java.lang.Object, java.lang.Object[])",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.isTruthy(java.lang.Object, boolean)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.rangeIterator(java.lang.Object, java.lang.Object, int, java.lang.String, int, int, int, int)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.recordContextVariable(io.github.minh124199.viettemplate.api.RenderContext, java.lang.String, java.lang.Object)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.toIterator(java.lang.Object, io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy, java.lang.String, int, int, int, int)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.toUtf8Bytes(java.lang.String)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.unaryOp(java.lang.Object, int, java.lang.String, int, int, int, int)",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeConst(io.github.minh124199.viettemplate.api.TemplateOutput, java.lang.String, byte[])",
                    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeValue(java.lang.Object, io.github.minh124199.viettemplate.api.TemplateOutput, int, int, java.lang.String, boolean, java.lang.String, int, int, int, int, io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy)",
                ],
                "viet_template_fields": [],
            }
        }

    def test_validation_passes_on_valid_report(self):
        res = verifier.validate_abi(self.valid_report, self.baseline)
        self.assertEqual(res["status"], "PASSED")
        self.assertEqual(res["errors"], [])
        self.assertTrue(res["type_count_matches"])
        self.assertTrue(res["method_count_matches"])
        self.assertTrue(res["field_count_matches"])
        self.assertEqual(res["total_types"], 7)
        self.assertEqual(res["total_methods"], 22)
        self.assertEqual(res["total_fields"], 0)

    def test_validation_detects_unregistered_type(self):
        import copy

        report = copy.deepcopy(self.valid_report)
        report["runtime_abi"]["viet_template_types"].append(
            "io.github.minh124199.viettemplate.api.UnregisteredSecretType"
        )
        res = verifier.validate_abi(report, self.baseline)
        self.assertEqual(res["status"], "FAILED")
        self.assertIn("io.github.minh124199.viettemplate.api.UnregisteredSecretType", res["unregistered_types"])
        self.assertTrue(
            any("unregistered type" in err and "UnregisteredSecretType" in err for err in res["errors"])
        )

    def test_validation_detects_unregistered_method(self):
        import copy

        report = copy.deepcopy(self.valid_report)
        # Replace one valid method with an unregistered method on BytecodeRuntimeBridge
        report["runtime_abi"]["viet_template_methods"][0] = (
            "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.unregisteredBridgeMethod(int)"
        )
        res = verifier.validate_abi(report, self.baseline)
        self.assertEqual(res["status"], "FAILED")
        self.assertTrue(
            any("unregisteredBridgeMethod" in m for m in res["unregistered_methods"])
        )
        self.assertTrue(
            any("unregistered method" in err and "unregisteredBridgeMethod" in err for err in res["errors"])
        )

    def test_validation_detects_unexpected_field(self):
        import copy

        report = copy.deepcopy(self.valid_report)
        report["runtime_abi"]["viet_template_fields"].append(
            "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.LEAKED_FIELD"
        )
        res = verifier.validate_abi(report, self.baseline)
        self.assertEqual(res["status"], "FAILED")
        self.assertFalse(res["field_count_matches"])
        self.assertTrue(
            any("LEAKED_FIELD" in f for f in res["unregistered_fields"])
        )
        self.assertTrue(
            any("unregistered field" in err or "Field count mismatch" in err for err in res["errors"])
        )

    def test_validation_detects_type_count_mismatch(self):
        import copy

        report = copy.deepcopy(self.valid_report)
        # Drop one type
        report["runtime_abi"]["viet_template_types"].pop()
        res = verifier.validate_abi(report, self.baseline)
        self.assertEqual(res["status"], "FAILED")
        self.assertFalse(res["type_count_matches"])
        self.assertTrue(any("Type count mismatch" in err or "type count mismatch" in err.lower() for err in res["errors"]))

    def test_validation_detects_method_count_mismatch(self):
        import copy

        report = copy.deepcopy(self.valid_report)
        # Drop one method
        report["runtime_abi"]["viet_template_methods"].pop()
        res = verifier.validate_abi(report, self.baseline)
        self.assertEqual(res["status"], "FAILED")
        self.assertFalse(res["method_count_matches"])
        self.assertTrue(any("Method count mismatch" in err or "method count mismatch" in err.lower() for err in res["errors"]))


if __name__ == "__main__":
    unittest.main()
