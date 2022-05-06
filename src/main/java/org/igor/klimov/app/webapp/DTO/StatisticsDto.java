package org.igor.klimov.app.webapp.DTO;

import java.util.List;

public class StatisticsDto {

    private TotalStatistics total;
    private List<SiteDto> detailed;

    public StatisticsDto(TotalStatistics total, List<SiteDto> detailed) {
        this.total = total;
        this.detailed = detailed;
    }

    public TotalStatistics getTotal() {
        return total;
    }

    public void setTotal(TotalStatistics total) {
        this.total = total;
    }

    public List<SiteDto> getDetailed() {
        return detailed;
    }

    public void setDetailed(List<SiteDto> detailed) {
        this.detailed = detailed;
    }
}
