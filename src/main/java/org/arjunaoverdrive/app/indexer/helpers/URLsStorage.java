package org.arjunaoverdrive.app.indexer.helpers;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.model.Page;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class URLsStorage {
    private final String root;
    private final Set<String> children; //container for absolute paths
    private final Set<String> savedPagesPaths;
    private final Set<Page> buffer;
    private final PageRepository pageRepository;

    private final ConfigProperties props;
    private static final String REFERRER = "http://www.google.com";
    private final static Logger LOGGER = Logger.getLogger(URLsStorage.class);

    public URLsStorage(String root, PageRepository pageRepository, ConfigProperties props) {
        this.root = root;
        this.pageRepository = pageRepository;
        this.buffer = Collections.synchronizedSet(new HashSet<>());
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

    public List<Page> savePages() {
        Set<Page> pageSet = new HashSet<>(buffer);
        clearBuffer();
        List<Page> savedPages = null;
        try {
            savedPages = pageRepository.saveAll(pageSet);
        } catch (Exception e) {
            LOGGER.error(e + " " + root);
        }
        LOGGER.info("Successfully saved " + savedPages.size() + " pages; " +
                "count " + (savedPagesPaths.size()) + " site " + root);
        return savedPages;
    }

    public Page createPageObject(Connection connection, int siteId) throws UnsupportedMimeTypeException {
        Page page;
        try {
            page = getNormalPage(connection, siteId);
        } catch (HttpStatusException hse) {
            return getFailedPage(connection, siteId, hse);
        } catch (UnsupportedMimeTypeException e) {
            throw new UnsupportedMimeTypeException("Unsupported type", e.getMimeType(), e.getUrl());
        } catch (Exception e) {
            return getPageWith500Status(connection, siteId);
        }
        return page;
    }

    private Page getPageWith500Status(Connection connection, int siteId) {
        String path = URLDecoder.decode(connection.request().url().getPath(), StandardCharsets.UTF_8);
        return new Page(path, 500, "", siteId);
    }

    private Page getFailedPage(Connection connection, int siteId, HttpStatusException hse) {
        String url = connection.request().url().getPath();
        String path = URLDecoder.decode(url, StandardCharsets.UTF_8);
        return new Page(path, hse.getStatusCode(), "", siteId);
    }

    private Page getNormalPage(Connection connection, int siteId) throws Exception {
        Connection.Response response = connection.execute();
        int statusCode = response.statusCode();
        String path = URLDecoder.decode(response.url().getPath(), StandardCharsets.UTF_8);
        String uri = isDomainEqualRoot() ? getUriWOSubdomain(path) : path;
        String content = clearContentFromExtraElements(response.body());
        return new Page(uri, statusCode, content, siteId);
    }

    private String clearContentFromExtraElements(String html) {
        Document document = Jsoup.parse(html, "UTF-8");

        document.getElementsByTag("header").remove();
        document.getElementsByTag("script").remove();
        document.getElementsByTag("noscript").remove();
        document.getElementsByTag("footer").remove();
        return document.html();
    }

    private boolean isDomainEqualRoot(){
        return root.substring(root.indexOf("//") + 2).contains("/");
    }

    private String getSubdomain(){
        String urlWOProtocol = root.substring(root.lastIndexOf("//") + 2);
        return urlWOProtocol.substring(urlWOProtocol.indexOf("/"));
    }

    private String getUriWOSubdomain(String uri){
        return uri.substring(getSubdomain().length());
    }

    private String getUserAgent() {
        return props.getUserAgent();
    }

    public int getBufferMaxSize() {
        return props.getBufferSize();
    }

    public Connection getConnection(String path) {
        String userAgent = getUserAgent();
        return Jsoup.connect(path).userAgent(userAgent).referrer(REFERRER);
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
