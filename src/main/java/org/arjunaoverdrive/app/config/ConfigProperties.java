package org.arjunaoverdrive.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "custom")
public class ConfigProperties {

    private String userAgent;

    private Set<Site> sites;

    private int bufferSize;

    private float frequencyThreshold;

    private String userName;

    private String userPassword;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public void setSites(Set<Site> sites) {
        this.sites = sites;
    }

    public Set<Site> getSites() {
        return sites;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public float getFrequencyThreshold() {
        return frequencyThreshold;
    }

    public void setFrequencyThreshold(float frequencyThreshold) {
        this.frequencyThreshold = frequencyThreshold;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }



    public static class Site{

        public Site() {
        }

        public Site(String url, String name) {
            this.url = url;
            this.name = name;
        }

        private String url;
        private String name;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
