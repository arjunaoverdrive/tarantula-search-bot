package main.app.webapp.DTO;

import java.util.List;

public class SearchDto {
    private final boolean result = true;

    public SearchDto(int count, List<SearchResultDto> data) {
        this.count = count;
        this.data = data;
    }

    private int count;
    private List<SearchResultDto> data;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isResult() {
        return result;
    }

    public List<SearchResultDto> getData() {
        return data;
    }

    public void setData(List<SearchResultDto> data) {
        this.data = data;
    }

}
