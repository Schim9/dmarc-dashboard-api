package lu.kaminski.dmarc.dto;

import java.util.List;

public record TrendDto(List<String> labels, List<DatasetDto> datasets) {
    public record DatasetDto(String domain, List<Double> data) {}
}
