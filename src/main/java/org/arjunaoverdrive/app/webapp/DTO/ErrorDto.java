package org.arjunaoverdrive.app.webapp.DTO;

import java.util.List;

public class ErrorDto {
    private List<ErrorPage> errorPages;

    public ErrorDto(List<ErrorPage> errorPages) {
        this.errorPages = errorPages;
    }

    public List<ErrorPage> getErrorPages() {
        return errorPages;
    }

    public void setErrorPages(List<ErrorPage> errorPages) {
        this.errorPages = errorPages;
    }

    public static class ErrorPage{
        private String url;
        private Integer code;

        public ErrorPage(String url, Integer code) {
            this.url = url;
            this.code = code;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }
    }
}
