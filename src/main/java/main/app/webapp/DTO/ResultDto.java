package main.app.webapp.DTO;

public class ResultDto {
    private final boolean result;

    public ResultDto(boolean result) {
        this.result = result;
    }
    public boolean isResult() {
        return result;
    }

    public static class Success extends ResultDto{
        public Success() {
            super(true);
        }
    }

    public static class Error extends ResultDto{
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
