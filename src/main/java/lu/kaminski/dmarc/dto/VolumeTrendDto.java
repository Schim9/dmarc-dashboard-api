package lu.kaminski.dmarc.dto;

import java.util.List;

public record VolumeTrendDto(
        List<String> labels,
        List<DatasetDto> datasets
) {
    public record DatasetDto(String domain, List<Long> passData, List<Long> failData) {}
}
