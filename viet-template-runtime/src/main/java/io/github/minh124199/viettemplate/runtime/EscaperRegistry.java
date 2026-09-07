package io.github.minh124199.viettemplate.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping {@link EscapeMode} and context names to {@link Escaper} instances.
 */
public final class EscaperRegistry {

  private static final EscaperRegistry DEFAULT_INSTANCE = new EscaperRegistry();

  private final Map<EscapeMode, Escaper> byMode = new ConcurrentHashMap<>();
  private final Map<String, Escaper> byName = new ConcurrentHashMap<>();

  /** Constructs an EscaperRegistry initialized with standard escapers. */
  public EscaperRegistry() {
    register(StandardEscapers.htmlText());
    register(StandardEscapers.htmlAttribute());
    register(StandardEscapers.urlComponent());
    register(StandardEscapers.jsString());
    register(StandardEscapers.cssString());
    register(StandardEscapers.raw());
  }

  /**
   * Returns the shared global default registry.
   *
   * @return the default registry
   */
  public static EscaperRegistry standard() {
    return DEFAULT_INSTANCE;
  }

  /**
   * Registers an escaper by its {@link EscapeMode} and default lowercase mode name.
   *
   * @param escaper the escaper to register
   */
  public void register(Escaper escaper) {
    Objects.requireNonNull(escaper, "escaper must not be null");
    byMode.put(escaper.mode(), escaper);
    byName.put(escaper.mode().name().toLowerCase(Locale.ROOT), escaper);
  }

  /**
   * Registers an escaper under a custom context name.
   *
   * @param name the context name
   * @param escaper the escaper to register
   */
  public void register(String name, Escaper escaper) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(escaper, "escaper must not be null");
    byName.put(name.toLowerCase(Locale.ROOT), escaper);
  }

  /**
   * Retrieves an escaper for the given escape mode.
   *
   * @param mode the escape mode
   * @return the registered escaper, or the standard default if unmapped
   */
  public Escaper get(EscapeMode mode) {
    Objects.requireNonNull(mode, "mode must not be null");
    Escaper escaper = byMode.get(mode);
    return escaper != null ? escaper : StandardEscapers.get(mode);
  }

  /**
   * Retrieves an escaper by context name.
   *
   * @param name the context name
   * @return the escaper, or null if not found
   */
  public Escaper get(String name) {
    Objects.requireNonNull(name, "name must not be null");
    return byName.get(name.toLowerCase(Locale.ROOT));
  }
}
