package lu.kaminski.dmarc.dto;

public record DkimSelectorCheckDto(
        String selector,
        boolean exists
) {}
