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
        private String uri;
        private String site;

        public ErrorPage(String uri, String name) {
            this.uri = uri;
            this.site = name;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site;
        }
    }
}
