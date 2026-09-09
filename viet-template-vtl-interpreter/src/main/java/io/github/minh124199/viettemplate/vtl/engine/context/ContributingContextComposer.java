package io.github.minh124199.viettemplate.vtl.engine.context;

import io.github.minh124199.viettemplate.api.ContextCollisionException;
import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.ValueOrigin;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Composes user model variables, contributor variables, and engine-internal variables into a single
 * unified execution context while enforcing variable collision and reserved-word policies.
 */
public final class ContributingContextComposer {

  private ContributingContextComposer() {}

  /** Result container holding the composed mutable render context and variable origin metadata. */
  public record CompositionResult(MutableRenderContext context, Map<String, ValueOrigin> origins) {
    public CompositionResult {
      Objects.requireNonNull(context, "context must not be null");
      origins = Collections.unmodifiableMap(new HashMap<>(origins));
    }
  }

  /**
   * Executes context contributors for a request and merges the resulting variables with the user
   * model.
   *
   * @param request current render request
   * @param contributors list of context contributors
   * @param policy collision resolution policy
   * @param reservedKeys engine reserved keys that cannot be overwritten
   * @param engineInternalVariables engine variables (e.g. screen_content)
   * @return composite evaluation context and origin map
   */
  public static CompositionResult compose(
      RenderRequest request,
      List<RenderContextContributor> contributors,
      ContextCollisionPolicy policy,
      Set<String> reservedKeys,
      Map<String, Object> engineInternalVariables) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    // 1. Run contributors
    Map<String, Object> contributed = new LinkedHashMap<>();
    if (contributors != null) {
      for (RenderContextContributor contributor : contributors) {
        DefaultContributorContext contributorContext = new DefaultContributorContext();
        contributor.contribute(contributorContext, request);
        for (Map.Entry<String, Object> entry : contributorContext.variables().entrySet()) {
          String k = entry.getKey();
          if (contributed.containsKey(k) && policy == ContextCollisionPolicy.ERROR_ON_COLLISION) {
            throw new ContextCollisionException(
                "Variable name collision on '" + k + "' between multiple context contributors",
                k,
                ValueOrigin.CONTRIBUTOR,
                ValueOrigin.CONTRIBUTOR);
          }
          contributed.put(k, entry.getValue());
        }
      }
    }

    RenderContext userModel = request.userModel();

    Map<String, Object> finalVariables = new LinkedHashMap<>();
    Map<String, ValueOrigin> origins = new LinkedHashMap<>();

    // 2. Reserved keys check for contributor variables
    if (reservedKeys != null) {
      for (String key : contributed.keySet()) {
        if (reservedKeys.contains(key)) {
          throw new ContextCollisionException(
              "Context contributor attempted to set reserved engine variable: " + key,
              key,
              ValueOrigin.ENGINE_INTERNAL,
              ValueOrigin.CONTRIBUTOR);
        }
      }
    }

    // 3. Reserved keys check for user model variables
    if (reservedKeys != null && userModel != null) {
      for (String key : userModel.keys()) {
        if (reservedKeys.contains(key)) {
          throw new ContextCollisionException(
              "User model attempted to set reserved engine variable: " + key,
              key,
              ValueOrigin.ENGINE_INTERNAL,
              ValueOrigin.MODEL);
        }
      }
    }

    // 4. Populate contributed variables
    for (Map.Entry<String, Object> e : contributed.entrySet()) {
      finalVariables.put(e.getKey(), e.getValue());
      origins.put(e.getKey(), ValueOrigin.CONTRIBUTOR);
    }

    // 5. Merge user model variables according to policy
    if (userModel != null) {
      for (String key : userModel.keys()) {
        Object modelVal = userModel.get(key);
        if (finalVariables.containsKey(key)) {
          // Collision between MODEL and CONTRIBUTOR
          switch (policy) {
            case ERROR_ON_COLLISION ->
                throw new ContextCollisionException(
                    "Variable name collision on '"
                        + key
                        + "' between user model and context contributor",
                    key,
                    ValueOrigin.MODEL,
                    ValueOrigin.CONTRIBUTOR);
            case MODEL_WINS -> {
              finalVariables.put(key, modelVal);
              origins.put(key, ValueOrigin.MODEL);
            }
            case CONTRIBUTOR_WINS -> {
              // Contributor value remains in finalVariables, origin remains CONTRIBUTOR
            }
          }
        } else {
          finalVariables.put(key, modelVal);
          origins.put(key, ValueOrigin.MODEL);
        }
      }
    }

    // 6. Inject engine internal variables
    if (engineInternalVariables != null) {
      for (Map.Entry<String, Object> entry : engineInternalVariables.entrySet()) {
        finalVariables.put(entry.getKey(), entry.getValue());
        origins.put(entry.getKey(), ValueOrigin.ENGINE_INTERNAL);
      }
    }

    MutableRenderContext ctx =
        MutableRenderContext.of(finalVariables, reservedKeys != null ? reservedKeys : Set.of());
    return new CompositionResult(ctx, origins);
  }

  public static final class DefaultContributorContext implements ContributorContext {
    private final Map<String, Object> entries = new LinkedHashMap<>();

    @Override
    public ContributorContext put(String key, Object value) {
      Objects.requireNonNull(key, "key must not be null");
      entries.put(key, value);
      return this;
    }

    @Override
    public ContributorContext putAll(Map<String, ?> newEntries) {
      if (newEntries != null) {
        newEntries.forEach(this::put);
      }
      return this;
    }

    @Override
    public boolean contains(String key) {
      return entries.containsKey(key);
    }

    @Override
    public Object get(String key) {
      return entries.get(key);
    }

    public Map<String, Object> variables() {
      return entries;
    }
  }
}
