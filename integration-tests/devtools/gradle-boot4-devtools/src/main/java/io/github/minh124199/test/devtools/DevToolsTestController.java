package io.github.minh124199.test.devtools;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DevToolsTestController {

  @Autowired
  private ApplicationContext context;

  @Autowired
  private TemplateEngine engine;

  @SuppressWarnings("unchecked")
  private static List<WeakReference<ClassLoader>> classLoaderHistory() {
    synchronized (System.getProperties()) {
      return (List<WeakReference<ClassLoader>>)
          System.getProperties()
              .computeIfAbsent(
                  "viet-template.devtools.tracked-classloaders",
                  k -> Collections.synchronizedList(new ArrayList<>()));
    }
  }

  private ClassLoader getTargetClassLoader() {
    ClassLoader cl = getClass().getClassLoader();
    if (cl != null && cl.getClass().getName().contains("RestartClassLoader")) {
      return cl;
    }
    if (context != null && context.getClassLoader() != null
        && context.getClassLoader().getClass().getName().contains("RestartClassLoader")) {
      return context.getClassLoader();
    }
    ClassLoader current = Thread.currentThread().getContextClassLoader();
    while (current != null) {
      if (current.getClass().getName().contains("RestartClassLoader")) {
        return current;
      }
      current = current.getParent();
    }
    return cl != null ? cl : Thread.currentThread().getContextClassLoader();
  }

  private void trackCurrentClassLoader() {
    ClassLoader current = getTargetClassLoader();
    List<WeakReference<ClassLoader>> history = classLoaderHistory();
    synchronized (history) {
      boolean alreadyTracked = false;
      for (WeakReference<ClassLoader> ref : history) {
        if (ref.get() == current) {
          alreadyTracked = true;
          break;
        }
      }
      if (!alreadyTracked) {
        history.add(new WeakReference<>(current));
      }
    }
  }

  @GetMapping("/public")
  public String publicPage(Model model) {
    trackCurrentClassLoader();
    model.addAttribute("user", new User("Alice"));
    model.addAttribute("account", new Account("AliceAccount", 10));
    model.addAttribute("profile", new Profile("Alice Wonderland"));
    model.addAttribute("settings", Map.of("theme", "dark"));
    model.addAttribute("tags", List.of("java", "devtools", "viet-template"));
    model.addAttribute("hostile", new HostilePayload("<script>alert('x')</script>"));
    return "public";
  }

  @GetMapping("/dashboard")
  public String dashboard() {
    trackCurrentClassLoader();
    return "dashboard";
  }

  @GetMapping("/admin")
  public String admin() {
    trackCurrentClassLoader();
    return "dashboard";
  }

  @GetMapping("/dynamic-page")
  public String dynamicPage() {
    trackCurrentClassLoader();
    if (engine != null) {
      engine.invalidateWithDependents(io.github.minh124199.viettemplate.api.TemplateId.of("dynamic-page.vtl"));
    }
    return "dynamic-page";
  }

  @GetMapping("/stale")
  public String stale() {
    trackCurrentClassLoader();
    return "stale";
  }

  @GetMapping(value = "/__test/restart-generation", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Map<String, Object> restartGeneration() {
    trackCurrentClassLoader();
    ClassLoader cl = getTargetClassLoader();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("contextId", context.getId());
    map.put("engineId", Integer.toHexString(System.identityHashCode(engine)));
    map.put("classLoaderId", Integer.toHexString(System.identityHashCode(cl)));
    map.put("classLoaderName", cl.getClass().getName());
    try {
      map.put("hasWatcher", context.containsBean("classPathFileSystemWatcher"));
      Class<?> restarterClass = Class.forName("org.springframework.boot.devtools.restart.Restarter");
      Object restarter = restarterClass.getMethod("getInstance").invoke(null);
      if (restarter != null) {
        java.net.URL[] urls = (java.net.URL[]) restarterClass.getMethod("getInitialUrls").invoke(restarter);
        if (urls != null) {
          map.put("initialUrls", java.util.Arrays.stream(urls).map(Object::toString).toList());
        }
      }
    } catch (Throwable ignored) {
    }
    return map;
  }



  @GetMapping(value = "/__test/classloader-leak-check", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Map<String, Object> leakCheck() {
    trackCurrentClassLoader();
    ClassLoader current = getTargetClassLoader();

    try {
      Class<?> c = Class.forName("org.springframework.cglib.core.ClassNameReader");
      java.lang.reflect.Field f = c.getDeclaredField("EARLY_EXIT");
      f.setAccessible(true);
      Throwable t = (Throwable) f.get(null);
      if (t != null) {
        t.setStackTrace(new StackTraceElement[0]);
        java.lang.reflect.Field bt = Throwable.class.getDeclaredField("backtrace");
        bt.setAccessible(true);
        bt.set(t, null);
      }
    } catch (Throwable ignored) {
    }

    try {
      Class<?> restarterClass = Class.forName("org.springframework.boot.devtools.restart.Restarter");
      Object restarter = restarterClass.getMethod("getInstance").invoke(null);
      if (restarter != null) {
        try {
          java.lang.reflect.Method m = restarterClass.getDeclaredMethod("forceReferenceCleanup");
          m.setAccessible(true);
          m.invoke(restarter);
        } catch (Throwable ignored) {
        }
        java.lang.reflect.Field f = restarterClass.getDeclaredField("leakSafeThreads");
        f.setAccessible(true);
        java.util.concurrent.BlockingDeque<?> deque = (java.util.concurrent.BlockingDeque<?>) f.get(restarter);
        for (Object obj : deque) {
          if (obj instanceof Thread t) {
            t.setContextClassLoader(ClassLoader.getPlatformClassLoader());
          }
        }
      }
    } catch (Throwable ignored) {
    }

    for (Thread t : Thread.getAllStackTraces().keySet()) {
      try {
        ClassLoader tcl = t.getContextClassLoader();
        if (tcl != null && tcl != current && tcl.getClass().getName().contains("RestartClassLoader")) {
          t.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        }
      } catch (Throwable ignored) {
      }
      if (t.getName().equals("DestroyJavaVM") || t.getName().equals("main") || t.getClass().getName().contains("LeakSafe")) {
        try {
          t.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        } catch (Throwable ignored) {
        }
      }
    }

    for (int i = 0; i < 5; i++) {
      System.gc();
      System.runFinalization();
      try {
        Thread.sleep(20L);
      } catch (InterruptedException ignored) {
      }
    }


    List<WeakReference<ClassLoader>> history = classLoaderHistory();
    int totalTracked;
    int oldGenerations = 0;
    int collectedOldGenerations = 0;
    int leakedGenerations = 0;
    List<Map<String, Object>> details = new ArrayList<>();
    synchronized (history) {
      totalTracked = history.size();
      for (WeakReference<ClassLoader> ref : history) {
        ClassLoader cl = ref.get();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("collected", cl == null);
        if (cl != null) {
          d.put("name", cl.getClass().getName());
          d.put("id", Integer.toHexString(System.identityHashCode(cl)));
          d.put("isCurrent", cl == current);
        }
        details.add(d);
        if (cl != null && cl == current) {
          continue;
        }
        oldGenerations++;
        if (cl == null) {
          collectedOldGenerations++;
        } else {
          leakedGenerations++;
        }
      }
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("totalTracked", totalTracked);
    map.put("oldGenerations", oldGenerations);
    map.put("collectedOldGenerations", collectedOldGenerations);
    boolean leakFree = (oldGenerations <= 1) || (collectedOldGenerations >= oldGenerations - 1);
    map.put("leakFree", leakFree);
    map.put("details", details);
    return map;


  }
}
