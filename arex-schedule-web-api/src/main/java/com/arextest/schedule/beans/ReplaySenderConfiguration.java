package com.arextest.schedule.beans;

import com.arextest.common.model.classloader.RemoteJarClassLoader;
import com.arextest.common.utils.RemoteJarLoaderUtils;
import com.arextest.schedule.extension.invoker.ReplayExtensionInvoker;
import com.arextest.schedule.sender.ReplaySender;
import com.arextest.schedule.sender.ReplaySenderFactory;
import com.arextest.schedule.sender.impl.DefaultDubboReplaySender;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * @author: sldu
 * @date: 2023/7/19 11:18
 **/
@Configuration
@Slf4j
public class ReplaySenderConfiguration {
  @Value("${replay.sender.extension.jarPath}")
  private String jarFilePath;
  /**
   * Optional comma-separated list of extra jars (file path or http url) that
   * are appended to the invoker class loader. Use it to plug in a custom Dubbo
   * Protocol (or any SPI extension) that the replay consumer side needs but
   * that is not bundled inside dubboInvoker.jar. Every extra jar shares the
   * SAME class loader as Dubbo, so its classes can link against Dubbo and its
   * META-INF/dubbo(/internal)/ SPI files are visible to Dubbo's ExtensionLoader
   * (the thread context class loader is set to this loader while sending).
   */
  @Value("${replay.sender.extension.extraJarPaths:}")
  private String extraJarPaths;
  private static final String LOCAL_INVOKER_PATH = "lib/dubboInvoker.jar";
  private static final String NEW_INVOKER_PATH = "dubboInvoker.jar";

  @Bean("dubboInvokerLoader")
  @ConditionalOnProperty(name = "replay.sender.extension.switch", havingValue = "true")
  public RemoteJarClassLoader dubboInvokerLoader() {
    try {
      RemoteJarClassLoader baseLoader;
      if (StringUtils.isNotBlank(jarFilePath)) {
        baseLoader = RemoteJarLoaderUtils.loadJar(jarFilePath);
      } else {
        baseLoader = RemoteJarLoaderUtils.loadJar(loadLocalInvokerJar().getPath());
      }
      return appendExtraJars(baseLoader);
    } catch (Throwable t) {
      LOGGER.error("Load invoker jar failed, application startup blocked", t);
    }
    LOGGER.error("No invoker found, application startup blocked");
    throw new RuntimeException("No invoker found");
  }

  /**
   * Merge the configured extra jars into the SAME class loader that carries
   * Dubbo (dubboInvoker.jar), returning one {@link RemoteJarClassLoader} whose
   * URL array holds both. This is required for Dubbo SPI: Dubbo's
   * ExtensionLoader scans META-INF/dubbo(/internal)/... using the class loader
   * that DEFINED the Dubbo classes. If the extra jars were only exposed through
   * a CHILD loader, that defining (parent) loader could not see the child's
   * URLs, so a custom Protocol class would load fine via the child but Dubbo
   * would still report "no such extension". Putting every jar in one loader
   * makes getResources() enumerate all of them, so both the protocol class and
   * its SPI registration file are visible to Dubbo. When no extra jar is
   * configured the base loader is returned unchanged.
   */
  private RemoteJarClassLoader appendExtraJars(RemoteJarClassLoader baseLoader) {
    if (StringUtils.isBlank(extraJarPaths)) {
      return baseLoader;
    }
    List<URL> urls = new ArrayList<>(Arrays.asList(baseLoader.getURLs()));
    boolean added = false;
    for (String path : extraJarPaths.split(",")) {
      String trimmed = StringUtils.trimToNull(path);
      if (trimmed == null) {
        continue;
      }
      try {
        urls.add(trimmed.startsWith("http") ? new URL(trimmed)
            : new File(trimmed).toURI().toURL());
        added = true;
      } catch (IOException e) {
        LOGGER.error("Invalid extra invoker jar path: {}", trimmed, e);
      }
    }
    if (!added) {
      return baseLoader;
    }
    LOGGER.info("Merge extra invoker jars into the dubbo class loader: {}", urls);
    return new RemoteJarClassLoader(urls.toArray(new URL[0]), baseLoader.getParent());
  }

  @Bean("replayExtensionInvoker")
  @ConditionalOnBean(name = "dubboInvokerLoader")
  public List<ReplayExtensionInvoker> invokers(
      @Qualifier("dubboInvokerLoader") RemoteJarClassLoader loader) {
    List<ReplayExtensionInvoker> invokers = new ArrayList<>(
        RemoteJarLoaderUtils.loadService(ReplayExtensionInvoker.class, loader));

    if (invokers.isEmpty()) {
      LOGGER.error("No invoker found, application startup blocked");
      throw new RuntimeException("No invoker found");
    }
    LOGGER.info("Load invoker jar success, invokers: {}", invokers);
    return invokers;
  }

  @Bean
  @ConditionalOnBean(name = "replayExtensionInvoker")
  public DefaultDubboReplaySender dubboReplaySender(
      @Value("#{'${arex.replay.header.excludes.dubbo}'.split(',')}") List<String> excludes,
      List<ReplayExtensionInvoker> invokers,
      @Qualifier("dubboInvokerLoader") RemoteJarClassLoader loader) {

    return new DefaultDubboReplaySender(excludes, invokers, loader);
  }

  @Bean
  public ReplaySenderFactory replaySenderFactory(List<ReplaySender> senders) {
    return new ReplaySenderFactory(senders);
  }

  private void inputStreamToFile(InputStream inputStream, String filePath) throws IOException {
    File file = new File(filePath);
    try (FileOutputStream fos = new FileOutputStream(file)) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) > 0) {
        fos.write(buffer, 0, length);
      }
      inputStream.close();
    }
  }

  // load .jar by inputstream and write to file.
  private URL loadLocalInvokerJar() throws IOException {
    ClassPathResource classPathResource = new ClassPathResource(LOCAL_INVOKER_PATH);
    InputStream inputStream = classPathResource.getInputStream();
    inputStreamToFile(inputStream, NEW_INVOKER_PATH);

    File file = new File(NEW_INVOKER_PATH);
    return file.toURI().toURL();
  }
}
