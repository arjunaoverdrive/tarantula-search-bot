package main.app.webapp.DTO;

import java.util.List;

public class SearchDto {
    private final boolean result;

    public SearchDto(boolean result) {
        this.result = result;
    }

    public boolean isResult() {
        return result;
    }


    public static class Success extends SearchDto {
        private int count;
        private List<SearchResultDto> data;

        public Success(int count, List<SearchResultDto> data) {
            super(true);
            this.count = count;
            this.data = data;
        }
        public int getCount() {
            return count;
        }

    public void setCount(int count) {
        this.count = count;
    }

    public List<SearchResultDto> getData() {
        return data;
    }

    public void setData(List<SearchResultDto> data) {
        this.data = data;
    }
}

    public static class Error extends SearchDto{
        private String error;

        public Error(String error) {
            super(false);
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }



}
