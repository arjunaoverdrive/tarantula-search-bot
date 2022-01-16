package main.app.webapp.DTO;

public class ResultDto {
    private boolean result;

    public ResultDto() {
        this.result = result;
    }

    public static class Success extends ResultDto{
        private final boolean result;

        public Success() {
            this.result = true;
        }

        public boolean isResult() {
            return result;
        }
    }

    public static class Error extends ResultDto{
        private final boolean result;
        private String error;

        public Error(String error) {
            this.result = false;
            this.error = error;
        }

        public boolean isResult() {
            return result;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
