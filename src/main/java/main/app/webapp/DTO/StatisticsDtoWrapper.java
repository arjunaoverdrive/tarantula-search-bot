package main.app.webapp.DTO;

public class StatisticsDtoWrapper {
    private boolean result;
    private StatisticsDto statistics;

    public StatisticsDtoWrapper() {
    }

    public StatisticsDtoWrapper(boolean result, StatisticsDto statistics) {
        this.result = result;
        this.statistics = statistics;
    }

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public StatisticsDto getStatistics() {
        return statistics;
    }

    public void setStatistics(StatisticsDto statistics) {
        this.statistics = statistics;
    }
}
