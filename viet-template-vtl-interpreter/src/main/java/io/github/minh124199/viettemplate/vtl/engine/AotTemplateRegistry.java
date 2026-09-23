package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encapsulates AOT template discovery via {@code templates.idx}, class loading, precompiled
 * instance preparation, and canonical AOT lookup.
 */
final class AotTemplateRegistry {

  private final EngineFingerprint fingerprint;
  private final VtlInterpreterOptions interpreterOptions;
  private final VtlSemanticOptions semanticOptions;
  private final VtlInterpreter interpreter;
  private volatile Map<TemplateId, PreparedAotTemplate> aotTemplates;

  AotTemplateRegistry(
      TemplateRepository repository,
      EngineFingerprint fingerprint,
      VtlInterpreterOptions interpreterOptions,
      VtlSemanticOptions semanticOptions,
      VtlInterpreter interpreter) {
    this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    this.interpreterOptions =
        Objects.requireNonNull(interpreterOptions, "interpreterOptions must not be null");
    this.semanticOptions =
        Objects.requireNonNull(semanticOptions, "semanticOptions must not be null");
    this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    this.aotTemplates = prepareAotRegistry(discoverAotTemplates(repository));
  }

  static AotTemplateRegistry create(
      TemplateRepository repository,
      EngineFingerprint fingerprint,
      VtlInterpreterOptions interpreterOptions,
      VtlSemanticOptions semanticOptions,
      VtlInterpreter interpreter) {
    return new AotTemplateRegistry(
        repository, fingerprint, interpreterOptions, semanticOptions, interpreter);
  }

  Optional<Template> find(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    Map<TemplateId, PreparedAotTemplate> currentAotTemplates = aotTemplates;
    PreparedAotTemplate aotTemplate = currentAotTemplates.get(id);
    boolean canonicalLookup = aotTemplate != null;
    if (!canonicalLookup) {
      try {
        aotTemplate = currentAotTemplates.get(TemplateId.normalize(id.value()));
      } catch (Exception ignored) {
      }
    }
    if (aotTemplate != null) {
      Template template =
          canonicalLookup
              ? aotTemplate.getOrPrepare(this)
              : createAotTemplate(id, aotTemplate.compiledClass());
      return Optional.of(template);
    }
    return Optional.empty();
  }

  boolean contains(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    Map<TemplateId, PreparedAotTemplate> currentAotTemplates = aotTemplates;
    if (currentAotTemplates.containsKey(id)) {
      return true;
    }
    try {
      return currentAotTemplates.containsKey(TemplateId.normalize(id.value()));
    } catch (Exception ignored) {
      return false;
    }
  }

  Map<TemplateId, PreparedAotTemplate> canonicalAotTemplates() {
    return aotTemplates;
  }

  Set<TemplateId> templateIds() {
    return aotTemplates.keySet();
  }

  void close() {
    aotTemplates = Map.of();
  }

  Template createAotTemplate(TemplateId id, Class<? extends CompiledTemplate> compiledClass) {
    try {
      String accessPolicyId = interpreterOptions.securityPolicy().policyFingerprint();
      String modelSignature = semanticOptions.modelSchema().parameters().toString();
      String backendHash =
          interpreterOptions.profile().name() + ":" + semanticOptions.profile().name();
      CompiledTemplate compiledTemplate = compiledClass.getDeclaredConstructor().newInstance();
      CompileCacheKey key =
          CompileCacheKey.of(
              id,
              "aot",
              fingerprint.compilerVersion(),
              fingerprint.optimizationLevel(),
              ExecutionTier.AOT_BYTECODE,
              accessPolicyId,
              modelSignature,
              backendHash,
              "");
      CompiledTemplateHandle handle =
          CompiledTemplateHandle.ofBytecode(id, 0L, key, compiledTemplate, null, null);
      return new VtlTemplate(
          TemplateDescriptor.of(id, ExecutionTier.AOT_BYTECODE.name()),
          handle,
          SourceText.of(id, ""),
          interpreterOptions,
          interpreter);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to instantiate AOT compiled template: " + id.value(), e);
    }
  }

  static final class PreparedAotTemplate {
    private final TemplateId id;
    private final Class<? extends CompiledTemplate> compiledClass;
    private final ReentrantLock preparationLock = new ReentrantLock();
    private volatile Template prepared;

    PreparedAotTemplate(TemplateId id, Class<? extends CompiledTemplate> compiledClass) {
      this.id = id;
      this.compiledClass = compiledClass;
    }

    Class<? extends CompiledTemplate> compiledClass() {
      return compiledClass;
    }

    Template getOrPrepare(AotTemplateRegistry registry) {
      Template current = prepared;
      if (current != null) {
        return current;
      }
      preparationLock.lock();
      try {
        current = prepared;
        if (current == null) {
          current = registry.createAotTemplate(id, compiledClass);
          prepared = current;
        }
        return current;
      } finally {
        preparationLock.unlock();
      }
    }
  }

  private static Map<TemplateId, Class<? extends CompiledTemplate>> discoverAotTemplates(
      TemplateRepository repository) {
    Map<TemplateId, Class<? extends CompiledTemplate>> registry = new HashMap<>();

    Set<ClassLoader> classLoaders = new LinkedHashSet<>();
    ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
    if (contextCl != null) {
      classLoaders.add(contextCl);
    }
    if (repository instanceof ClasspathTemplateRepository) {
      try {
        Field clField = ClasspathTemplateRepository.class.getDeclaredField("classLoader");
        clField.setAccessible(true);
        ClassLoader repoCl = (ClassLoader) clField.get(repository);
        if (repoCl != null) {
          classLoaders.add(repoCl);
        }
      } catch (Exception ignored) {
      }
    }
    ClassLoader engineCl = AotTemplateRegistry.class.getClassLoader();
    if (engineCl != null) {
      classLoaders.add(engineCl);
    }

    for (ClassLoader cl : classLoaders) {
      try {
        Enumeration<URL> resources = cl.getResources("META-INF/viet-template/templates.idx");
        while (resources != null && resources.hasMoreElements()) {
          URL url = resources.nextElement();
          loadTemplateIndex(url, cl, registry);
        }
      } catch (IOException ignored) {
      }
    }

    return Collections.unmodifiableMap(registry);
  }

  private static Map<TemplateId, PreparedAotTemplate> prepareAotRegistry(
      Map<TemplateId, Class<? extends CompiledTemplate>> discoveredTemplates) {
    if (discoveredTemplates.isEmpty()) {
      return Map.of();
    }

    Map<TemplateId, PreparedAotTemplate> preparedTemplates =
        new HashMap<>(discoveredTemplates.size());
    for (Map.Entry<TemplateId, Class<? extends CompiledTemplate>> entry :
        discoveredTemplates.entrySet()) {
      preparedTemplates.put(
          entry.getKey(), new PreparedAotTemplate(entry.getKey(), entry.getValue()));
    }
    return Collections.unmodifiableMap(preparedTemplates);
  }

  private static void loadTemplateIndex(
      URL url, ClassLoader cl, Map<TemplateId, Class<? extends CompiledTemplate>> registry) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq > 0) {
          String idStr = line.substring(0, eq).trim();
          String fqcn = line.substring(eq + 1).trim();
          if (!idStr.isEmpty() && !fqcn.isEmpty()) {
            TemplateId templateId;
            try {
              templateId = TemplateId.normalize(idStr);
            } catch (Exception e) {
              templateId = TemplateId.of(idStr);
            }
            try {
              Class<?> loaded = cl.loadClass(fqcn);
              if (CompiledTemplate.class.isAssignableFrom(loaded)) {
                @SuppressWarnings("unchecked")
                Class<? extends CompiledTemplate> compiledClass =
                    (Class<? extends CompiledTemplate>) loaded;
                registry.putIfAbsent(templateId, compiledClass);
              }
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
          }
        }
      }
    } catch (IOException ignored) {
    }
  }
}
