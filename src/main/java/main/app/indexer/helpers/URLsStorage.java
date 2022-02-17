package main.app.indexer.helpers;

import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.ConfigProperties;
import main.app.model.Page;
import main.app.model.Site;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class URLsStorage {
    private final String root;
    private final Set<String> children; //container for absolute paths
    private final Set<String> savedPagesPaths;
    private final Set<Page> buffer;
    private final PageRepository pageRepository;

    private final ConfigProperties props;
    private final static Logger LOGGER = Logger.getLogger(URLsStorage.class);


    public URLsStorage(String root, PageRepository pageRepository, ConfigProperties props) {
        this.root = root;
        this.pageRepository = pageRepository;
        this.buffer = new HashSet<>();
        this.children = new HashSet<>();
        this.savedPagesPaths = new HashSet<>();
        this.props = props;
    }

    public boolean addPath(String path) {
        return children.add(path);
    }

    public boolean addPageURL(String path) {
        return savedPagesPaths.add(path);
    }

    public void addPage2Buffer(Page page) {
        buffer.add(page);
    }

    public Set<Page> getBuffer() {
        return buffer;
    }

    public void clearBuffer() {
        buffer.clear();
    }

    public String getRoot() {
        return root;
    }

    public List<Page> doWrite() {
        Set<Page> pageSet = new HashSet<>(buffer);
        clearBuffer();
        List<Page> savedPages = null;
        try {
            savedPages = pageRepository.saveAll(pageSet);
        }catch (Exception e){
           LOGGER.error(e + " " + root);
        }
        LOGGER.info("Successfully saved " + savedPages.size() + " pages; " +
                "count " + (savedPagesPaths.size()) + " site " + root);
        return savedPages;
    }

    public Page createPageObject(Connection connection, int siteId, SiteRepository siteRepository) throws UnsupportedMimeTypeException {
        Page page;
        Connection.Response response;
        try {
            response = connection.execute();
            int statusCode =  response.statusCode();
            String path = URLDecoder.decode(response.url().getPath(), StandardCharsets.UTF_8);
            page = new Page(path, statusCode, response.body(), siteId);
        } catch (HttpStatusException hse) {
            Site site = siteRepository.findById(siteId).get();
            String path = URLDecoder.decode(hse.getUrl().substring(site.getUrl().length()), StandardCharsets.UTF_8);
            page = new Page(path, hse.getStatusCode(), "", siteId);
            return page;
        } catch (UnsupportedMimeTypeException e) {
            throw new UnsupportedMimeTypeException("Unsupported type", e.getMimeType(), e.getUrl());
        } catch (IOException e) {
            String path = URLDecoder.decode(connection.request().url().getPath(), StandardCharsets.UTF_8);
            page = new Page(path, 500, "", siteId);
            LOGGER.warn(e + " " + path);
            return page;
        }
        return page;
    }

    private String getUserAgent() {
        return props.getUserAgent();
    }

    public Connection getConnection(String path) {
        String userAgent = getUserAgent();
        return Jsoup.connect(path).userAgent(userAgent).referrer("http://www.google.com");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof URLsStorage)) return false;
        URLsStorage that = (URLsStorage) o;
        return root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root);
    }
}