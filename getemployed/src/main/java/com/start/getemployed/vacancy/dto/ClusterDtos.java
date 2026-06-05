package com.start.getemployed.vacancy.dto;

import java.util.List;

public class ClusterDtos {

  // Корень ответа от HH, если мы запрашиваем только кластеры
  public record HhClustersResponse(List<Cluster> clusters) {}

  // Сам кластер (например, кластер Навыков)
  public record Cluster(
      String id, // "skills", "area", "employer"
      String name, // "Ключевые навыки", "Регион"
      List<ClusterItem> items) {}

  // Элемент кластера (конкретный навык и сколько вакансий его требуют)
  public record ClusterItem(
      String name, // "Spring Boot"
      String url, // URL для поиска конкретно по этому навыку
      Integer count // Количество вакансий на рынке с этим навыком
      ) {}
}
