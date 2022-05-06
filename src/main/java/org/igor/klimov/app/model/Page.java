package org.igor.klimov.app.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "page", indexes = @javax.persistence.Index(columnList = "site_id, path"))
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "path", columnDefinition = "TEXT NOT NULL")
    private String path;
    @Column(name = "code", columnDefinition = "INT NOT NULL")
    private int code;
    @Column(name = "content", columnDefinition = "TEXT NOT NULL")
    private String content;
    @Column(name = "site_id", columnDefinition = "INTEGER NOT NULL")
    private Integer siteId;

    public Page() {
    }

    public Page(String path, int code, String content, Integer siteId) {
        this.path = path;
        this.code = code;
        this.content = content;
        this.siteId = siteId;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSiteId() {
        return siteId;
    }

    public void setSiteId(int siteId) {
        this.siteId = siteId;
    }

    @Override
    public String toString() {
        return "Page{" +
                "path='" + path + '\'' +
                ", code=" + code +
                ", content='" + content + '\'' +
                ", site=" + siteId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Page page = (Page) o;
        return path.equals(page.path) && Objects.equals(content, page.content) && siteId.equals(page.siteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, content, siteId);
    }
}
