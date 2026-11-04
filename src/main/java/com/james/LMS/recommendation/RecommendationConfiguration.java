package com.james.LMS.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class RecommendationConfiguration {
  private static final Logger log = LoggerFactory.getLogger(RecommendationConfiguration.class);

  @Bean
  public KnnConfig recommendationKnnConfig(ObjectMapper mapper) {
    var resource = new ClassPathResource("recommendation/knn-config.json");
    if (!resource.exists()) {
      log.warn("KNN config missing; using safe built-in default");
      return KnnConfig.defaults();
    }
    try (var in = resource.getInputStream()) {
      KnnConfig config = mapper.readValue(in, KnnConfig.class);
      log.info("KNN config loaded, dimension={}, k={}", config.hashDimension(), config.defaultK());
      return config;
    } catch (Exception e) {
      log.warn("KNN config invalid; using safe built-in default: {}", e.toString());
      return KnnConfig.defaults();
    }
  }

  @Bean(name = "recommendationScheduler")
  public ThreadPoolTaskScheduler recommendationScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("gocook-knn-sync-");
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }
}
