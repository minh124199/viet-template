#!/usr/bin/env python3
"""
Audits all broad exception catch blocks across production modules in Viet Template.
Produces a machine-readable JSON inventory: build/reports/exception-boundary-audit.json
"""

import os
import re
import json
from collections import Counter

PROD_MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
    "viet-template-spring",
    "viet-template-spring-security",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-boot-starter",
    "viet-template-tck",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
]

def find_enclosing_method(lines, catch_idx):
    for i in range(catch_idx, -1, -1):
        line = lines[i].strip()
        if line.startswith("//") or line.startswith("*") or line.startswith("/*"):
            continue
        m = re.search(r"(?:public|protected|private|static|final|\w+)\s+[\w\<\>\[\],\s]+\s+([A-Za-z0-9_]+)\s*\([^;]*\)\s*(?:throws\s+[\w\s,]+)?\s*\{?", line)
        if m:
            name = m.group(1)
            if name not in ("if", "for", "while", "switch", "catch", "synchronized", "new", "return"):
                return name
        m_ctor = re.search(r"(?:public|protected|private)\s+([A-Z][A-Za-z0-9_]+)\s*\([^;]*\)\s*(?:throws\s+[\w\s,]+)?\s*\{?", line)
        if m_ctor:
            return m_ctor.group(1)
    return "unknown"

def extract_fqcn(path, lines):
    pkg = ""
    for line in lines:
        line = line.strip()
        if line.startswith("package "):
            pkg = line.split()[1].rstrip(";")
            break
    cls = os.path.basename(path)
    if cls.endswith(".java"):
        cls = cls[:-5]
    return f"{pkg}.{cls}" if pkg else cls

def find_try_body(lines, catch_idx):
    try_idx = -1
    for i in range(catch_idx, -1, -1):
        if re.search(r"\btry\b", lines[i]):
            try_idx = i
            break
    if try_idx != -1:
        snippet = " ".join([l.strip() for l in lines[try_idx:catch_idx]])
        return snippet[:300]
    return "unknown"

def classify_and_prescribe(mod, fqcn, method, line_num, caught_type, var_name, try_snippet, catch_body):
    body = catch_body.strip()
    returns_fallback = ("return " in body or "Optional.empty()" in body) and "throw " not in body
    logs = any(term in body for term in ("log.", "logger.", "System.err", "LOG."))
    rethrows = "throw " in body and ("throw " + var_name in body or "throw new" in body or "throw e" in body or "throw t" in body)
    wraps = "throw new " in body and var_name in body
    restores_interrupt = "Thread.currentThread().interrupt()" in body
    
    hot_path = any(term in fqcn for term in ("Interpreter", "Bridge", "Reference", "Truthiness"))
    security_sensitive = "Security" in fqcn or "security" in mod.lower()
    framework_boundary = any(term in mod for term in ("spring", "quarkus", "plugin")) or "Mojo" in fqcn or "Task" in fqcn

    # Rule-based classification
    if "ClasspathTemplateRepository" in fqcn:
        operation = "Reading last-modified timestamp from resource URL connection"
        current_action = "Ignores exception and defaults lastModified timestamp to 0L"
        classification = "IO_FAILURE"
        recommended = "Catch IOException specifically instead of broad Exception"
    elif "TemplateEngineProvider" in fqcn:
        operation = "Reflective fallback instantiation of VtlTemplateEngineProvider"
        current_action = "Wraps into IllegalStateException with classpath guidance"
        classification = "FRAMEWORK_BOOTSTRAP_FAILURE"
        recommended = "Catch ReflectiveOperationException specifically instead of Exception"
    elif "QuarkusSecurityView" in fqcn:
        operation = "Extracting principal name and roles from SecurityIdentity"
        current_action = "Swallows all Throwables and returns ANONYMOUS"
        classification = "SECURITY_FAILURE"
        security_sensitive = True
        recommended = "P0: Rethrow VirtualMachineError and ThreadDeath before returning ANONYMOUS; catch Exception"
    elif "QuarkusSecurityRenderContextContributor" in fqcn and "identitySupplier" in try_snippet:
        operation = "Invoking custom SecurityIdentity supplier"
        current_action = "Swallows all Throwables and returns null"
        classification = "SECURITY_FAILURE"
        security_sensitive = True
        recommended = "P0: Rethrow VirtualMachineError and ThreadDeath before returning null; catch Exception"
    elif "QuarkusSecurityRenderContextContributor" in fqcn and "Arc.container" in try_snippet:
        operation = "Resolving SecurityIdentity bean from Arc CDI container"
        current_action = "Swallows all Throwables and returns null"
        classification = "OPTIONAL_CAPABILITY_PROBE"
        security_sensitive = True
        recommended = "P0: Rethrow VirtualMachineError and ThreadDeath; catch Exception"
    elif "VietTemplateProducer" in fqcn and "SecurityIdentity" in try_snippet:
        operation = "Probing Arc container for Quarkus SecurityIdentity bean"
        current_action = "Swallows all Throwables silently"
        classification = "OPTIONAL_CAPABILITY_PROBE"
        security_sensitive = True
        recommended = "P0: Rethrow VirtualMachineError and ThreadDeath; catch LinkageError/Exception"
    elif "VietTemplateProducer" in fqcn and "Charset.forName" in try_snippet:
        operation = "Parsing character encoding from configuration"
        current_action = "Falls back to StandardCharsets.UTF_8"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of Exception"
    elif "VietTemplateProducer" in fqcn and "UndefinedReferencePolicy.valueOf" in try_snippet:
        operation = "Parsing undefined-reference policy enum from configuration"
        current_action = "Falls back to UndefinedReferencePolicy.SILENT"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of Exception"
    elif "VietTemplateRenderer" in fqcn and "repository.find" in try_snippet:
        operation = "Probing repository for template existence"
        current_action = "Swallows Exception and falls through to engine.get"
        classification = "TEMPLATE_RESOLUTION_FAILURE"
        security_sensitive = True
        recommended = "P1: Propagate TemplateSecurityException; catch TemplateResourceException"
    elif "VietTemplateRenderer" in fqcn and "engine.get" in try_snippet:
        operation = "Probing engine for template compilation"
        current_action = "Converts TemplateResourceException to false, other Exceptions to true"
        classification = "TEMPLATE_RESOLUTION_FAILURE"
        security_sensitive = True
        recommended = "P1: Propagate TemplateSecurityException; do not mask security denials"
    elif "VietTemplateViewResolver" in fqcn and "repository.find" in try_snippet:
        operation = "Probing repository for template source existence in Spring ViewResolver"
        current_action = "Swallows Exception and falls through to engine.get"
        classification = "TEMPLATE_RESOLUTION_FAILURE"
        security_sensitive = True
        recommended = "P1: Propagate TemplateSecurityException; catch TemplateResourceException"
    elif "VietTemplateViewResolver" in fqcn and "engine.get" in try_snippet:
        operation = "Probing engine for view resolution in Spring ViewResolver"
        current_action = "Converts TemplateResourceException to false, other Exceptions to true"
        classification = "TEMPLATE_RESOLUTION_FAILURE"
        security_sensitive = True
        recommended = "P1: Propagate TemplateSecurityException; treat syntax/compilation as true but rethrow security denials"
    elif "VietTemplateAutoConfiguration" in fqcn:
        operation = "Probing ApplicationContext resource for AOT templates index"
        current_action = "Swallows Exception and returns false"
        classification = "OPTIONAL_CAPABILITY_PROBE"
        recommended = "Catch IOException and IllegalArgumentException specifically"
    elif "VietTemplateRuntimeHints" in fqcn and "registerType" in try_snippet:
        operation = "Registering Spring AOT reflection hints for template or provider class"
        current_action = "Swallows Exception or logs debug message"
        classification = "FRAMEWORK_BOOTSTRAP_FAILURE"
        recommended = "Catch IllegalArgumentException or ClassNotFoundException specifically"
    elif "VietTemplateRuntimeHints" in fqcn and ("parseIndexUrl" in method or "reader.readLine" in try_snippet):
        operation = "Parsing templates.idx URL stream in Spring RuntimeHints"
        current_action = "Logs debug message and aborts parsing"
        classification = "FRAMEWORK_BOOTSTRAP_FAILURE"
        recommended = "Catch IOException specifically instead of broad Exception"
    elif "VietTemplateCompileMojo" in fqcn and "Charset.forName" in try_snippet:
        operation = "Parsing encoding parameter in Maven Mojo"
        current_action = "Wraps into MojoExecutionException"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of Exception"
    elif "VietTemplateCompileMojo" in fqcn and "requestBuilder.build" in try_snippet:
        operation = "Building TemplateAotRequest in Maven Mojo"
        current_action = "Wraps into MojoExecutionException"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException and IllegalStateException specifically"
    elif "VietTemplateCompileTask" in fqcn and "Charset.forName" in try_snippet:
        operation = "Parsing encoding property in Gradle task"
        current_action = "Wraps into GradleException"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of Exception"
    elif "VietTemplateCompileTask" in fqcn and ("requestBuilder.build" in try_snippet or line_num == 134):
        operation = "Building TemplateAotRequest in Gradle task"
        current_action = "Wraps into GradleException"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException and IllegalStateException specifically"
    elif "VietTemplateProcessor" in fqcn:
        operation = "Parsing encoding configuration during Quarkus build augmentation"
        current_action = "Falls back to StandardCharsets.UTF_8"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of Exception"
    elif "TckRunner" in fqcn:
        operation = "Executing conformance test scenario"
        current_action = "Compares thrown Throwable with expected exception and diagnostic code"
        classification = "FRAMEWORK_BOOTSTRAP_FAILURE"
        recommended = "Rethrow VirtualMachineError and ThreadDeath before recording scenario result"
    elif "LinkedReferenceAccess" in fqcn and "link.invokeGet" in try_snippet:
        operation = "Invoking dynamic call site getter"
        current_action = "Wraps all Throwables in TemplateRenderException with SYNTAX_ERROR"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "P0: Rethrow VirtualMachineError/ThreadDeath; unwrap InvocationTargetException; use evaluation diagnostic code"
    elif "LinkedReferenceAccess" in fqcn and "link.invokeMethod" in try_snippet:
        operation = "Invoking dynamic call site zero-arg method"
        current_action = "Wraps all Throwables in TemplateRenderException with SYNTAX_ERROR"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "P0: Rethrow VirtualMachineError/ThreadDeath; unwrap InvocationTargetException; use evaluation diagnostic code"
    elif "BytecodeRuntimeBridge" in fqcn and caught_type == "Throwable":
        operation = "Dynamic invocation via MethodHandle / DynamicCallSite"
        current_action = "Rethrows RuntimeException, wraps non-RuntimeException in new RuntimeException"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "P0: Rethrow Error (including VirtualMachineError/ThreadDeath) directly without wrapping"
    elif "BytecodeRuntimeBridge" in fqcn and caught_type == "Exception":
        operation = "Reflective method invocation fallback"
        current_action = "Wraps into new RuntimeException(ex)"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "P1: Unwrap InvocationTargetException to preserve user application cause"
    elif "DevelopmentFileWatcher" in fqcn and (line_num == 75 or "SensitivityWatchEventModifier" in try_snippet):
        operation = "Reflective probe for JDK SensitivityWatchEventModifier"
        current_action = "Swallows all Throwables and returns empty modifiers"
        classification = "OPTIONAL_CAPABILITY_PROBE"
        recommended = "Rethrow VirtualMachineError and ThreadDeath; catch ReflectiveOperationException"
    elif "DevelopmentFileWatcher" in fqcn and "changeListener.accept" in try_snippet:
        operation = "Notifying file change listener on debounce trigger"
        current_action = "Swallows Exception silently"
        classification = "ENGINE_LIFECYCLE_FAILURE"
        recommended = "Catch RuntimeException; log warning rather than silent swallow"
    elif "DevelopmentFileWatcher" in fqcn and "watchService.close" in try_snippet:
        operation = "Closing WatchService during watcher shutdown"
        current_action = "Swallows Exception silently"
        classification = "ENGINE_LIFECYCLE_FAILURE"
        recommended = "Catch IOException specifically instead of broad Exception"
    elif "AotTemplateRegistry" in fqcn and line_num == 77:
        operation = "Normalizing TemplateId for secondary AOT cache lookup"
        current_action = "Swallows Exception and leaves aotTemplate null"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of broad Exception"
    elif "AotTemplateRegistry" in fqcn and line_num == 98:
        operation = "Normalizing TemplateId for secondary AOT cache contains check"
        current_action = "Swallows Exception and returns false"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of broad Exception"
    elif "AotTemplateRegistry" in fqcn and line_num == 137:
        operation = "Reflective instantiation of AOT CompiledTemplate instance"
        current_action = "Wraps Exception into IllegalStateException"
        classification = "TEMPLATE_COMPILATION_FAILURE"
        recommended = "Catch ReflectiveOperationException specifically instead of broad Exception"
    elif "AotTemplateRegistry" in fqcn and "getDeclaredField" in try_snippet:
        operation = "Reflective probe of ClasspathTemplateRepository classLoader field"
        current_action = "Swallows Exception and skips repository classLoader"
        classification = "OPTIONAL_CAPABILITY_PROBE"
        recommended = "Catch ReflectiveOperationException and SecurityException specifically"
    elif "AotTemplateRegistry" in fqcn and line_num == 250:
        operation = "Normalizing TemplateId from templates.idx line"
        current_action = "Falls back to TemplateId.of(idStr)"
        classification = "USER_INPUT_ERROR"
        recommended = "Catch IllegalArgumentException specifically instead of broad Exception"
    elif "VtlTemplateEngine" in fqcn and line_num == 102:
        operation = "Warming template via get(targetId) in engine resource resolver"
        current_action = "Swallows Exception silently"
        classification = "TEMPLATE_RESOLUTION_FAILURE"
        security_sensitive = True
        recommended = "P0: Propagate TemplateSecurityException and compilation errors; do not mask"
    elif "VtlTemplateEngine" in fqcn and line_num == 107:
        operation = "Resolving source via repository.find(targetId) in engine resource resolver"
        current_action = "Catches Exception and returns Optional.empty()"
        classification = "TEMPLATE_RESOLUTION_FAILURE"
        security_sensitive = True
        recommended = "P0: Propagate TemplateSecurityException and resource errors; do not mask as Optional.empty()"
    elif "VtlTemplateEngine" in fqcn and "changedPath" in try_snippet:
        operation = "Invalidating template on filesystem watcher change event"
        current_action = "Swallows Exception silently"
        classification = "ENGINE_LIFECYCLE_FAILURE"
        recommended = "Catch IllegalArgumentException specifically instead of broad Exception"
    elif "IrInterpreter" in fqcn and line_num == 941:
        operation = "Invoking DirectRecord accessor MethodHandle"
        current_action = "Falls back to frame.referenceAccess.getProperty on Exception"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "Catch Throwable and rethrow Error; fall back on reflective access failure"
    elif "IrInterpreter" in fqcn and line_num == 948:
        operation = "Invoking DirectGetter MethodHandle"
        current_action = "Falls back to frame.referenceAccess.getProperty on Exception"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "Catch Throwable and rethrow Error; fall back on reflective access failure"
    elif "IrInterpreter" in fqcn and line_num == 955:
        operation = "Reading DirectField VarHandle / Field"
        current_action = "Falls back to frame.referenceAccess.getProperty on Exception"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "Catch Throwable and rethrow Error; fall back on reflective access failure"
    elif "IrInterpreter" in fqcn and line_num == 970:
        operation = "Invoking ExtensionCall static method"
        current_action = "Falls back to frame.referenceAccess.getProperty on Exception"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "Catch Throwable and rethrow Error; fall back on reflective access failure"
    elif "IrInterpreter" in fqcn and (line_num == 992 or "inv.targetMethod().invoke" in try_snippet):
        operation = "Invoking allowed reflective method on target"
        current_action = "Wraps Exception in TemplateRenderException with INVALID_METHOD"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "P1: Unwrap InvocationTargetException, preserve user cause and meaningful message"
    elif "VtlInterpreter" in fqcn and line_num in (149, 241):
        operation = "Invoking AOT CompiledTemplate.render"
        current_action = "Catches TemplateRenderException and IOException, then wraps other Exceptions in TemplateRenderException(SYNTAX_ERROR)"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "P1: Propagate TemplateException (including security, limit, semantic) directly; do not misclassify as SYNTAX_ERROR"
    elif "VtlTruthiness" in fqcn:
        operation = "Probing custom boolean / emptiness / numeric conversion method on model object"
        current_action = "Swallows Exception and falls through to next truthiness rule"
        classification = "TEMPLATE_EVALUATION_FAILURE"
        recommended = "Rethrow VirtualMachineError and ThreadDeath if wrapped in InvocationTargetException; catch ReflectiveOperationException"
    elif "BytecodeTemplateCompiler" in fqcn and line_num == 382:
        operation = "Instantiating generated CompiledTemplate class"
        current_action = "Wraps Exception into IllegalStateException"
        classification = "TEMPLATE_COMPILATION_FAILURE"
        recommended = "Catch ReflectiveOperationException specifically instead of Exception"
    elif "BytecodeTemplateCompiler" in fqcn and line_num == 418:
        operation = "Initializing static fields on generated template class"
        current_action = "Wraps Exception into IllegalStateException"
        classification = "TEMPLATE_COMPILATION_FAILURE"
        recommended = "Catch ReflectiveOperationException specifically instead of Exception"
    else:
        operation = f"Protected operation in {method}"
        current_action = f"Handles {caught_type}"
        classification = "INTERNAL_INVARIANT_FAILURE"
        recommended = f"Audit boundary in {fqcn}#{method}"

    return {
        "operationBeingProtected": operation,
        "currentAction": current_action,
        "returnsFallback": returns_fallback,
        "logs": logs,
        "rethrows": rethrows,
        "wraps": wraps,
        "restoresInterrupt": restores_interrupt,
        "securitySensitive": security_sensitive,
        "frameworkBoundary": framework_boundary,
        "hotPath": hot_path,
        "classification": classification,
        "recommendedAction": recommended
    }

def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root_dir)
    
    broad_entries = []
    all_catches = []
    
    for mod in PROD_MODULES:
        src_dir = os.path.join(mod, "src", "main", "java")
        if not os.path.exists(src_dir):
            continue
        for r, _, files in os.walk(src_dir):
            for f in sorted(files):
                if not f.endswith(".java"):
                    continue
                path = os.path.join(r, f)
                rel_path = os.path.relpath(path, root_dir)
                with open(path) as fp:
                    lines = fp.readlines()
                fqcn = extract_fqcn(path, lines)
                for idx, line in enumerate(lines):
                    m = re.search(r"catch\s*\(\s*([A-Za-z0-9_\|\.\s]+)\s+([A-Za-z0-9_]+)\s*\)", line)
                    if not m:
                        continue
                    caught_type = m.group(1).strip()
                    var_name = m.group(2).strip()
                    method = find_enclosing_method(lines, idx)
                    
                    body_lines = []
                    brace_count = 0
                    started = False
                    for j in range(idx, min(len(lines), idx + 40)):
                        l = lines[j]
                        if "{" in l:
                            brace_count += l.count("{")
                            started = True
                        if "}" in l:
                            brace_count -= l.count("}")
                        if started:
                            body_lines.append(l.strip())
                            if brace_count <= 0:
                                break
                    catch_body = " ".join(body_lines)
                    try_snippet = find_try_body(lines, idx)
                    
                    catch_record = {
                        "module": mod,
                        "file": rel_path,
                        "fqcn": fqcn,
                        "method": method,
                        "line": idx + 1,
                        "caughtType": caught_type,
                        "var": var_name
                    }
                    all_catches.append(catch_record)
                    
                    if caught_type in ("Exception", "Throwable", "RuntimeException"):
                        props = classify_and_prescribe(mod, fqcn, method, idx + 1, caught_type, var_name, try_snippet, catch_body)
                        record = dict(catch_record)
                        record.update(props)
                        broad_entries.append(record)

    os.makedirs("build/reports", exist_ok=True)
    report_file = "build/reports/exception-boundary-audit.json"
    with open(report_file, "w") as fp:
        json.dump(broad_entries, fp, indent=2)
        
    print(f"Total production catch blocks: {len(all_catches)}")
    print(f"Broad catch blocks: {len(broad_entries)}")
    
    # Classification breakdown
    class_counts = Counter(e["classification"] for e in broad_entries)
    print("\nClassification breakdown:")
    for k, v in class_counts.most_common():
        print(f"  {k}: {v}")

    # Recommended action summary
    p0_count = sum(1 for e in broad_entries if e["recommendedAction"].startswith("P0"))
    p1_count = sum(1 for e in broad_entries if e["recommendedAction"].startswith("P1"))
    print(f"\nPriority breakdown in audit:")
    print(f"  P0 unsafe catches: {p0_count}")
    print(f"  P1 semantic ambiguity: {p1_count}")
    print(f"  P2 cleanup / targeted narrowing: {len(broad_entries) - p0_count - p1_count}")

if __name__ == "__main__":
    main()
