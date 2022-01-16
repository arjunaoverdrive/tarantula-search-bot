package main.app.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "page")
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 65535, unique = true)
    private String path;
    @Column(nullable = false)
    private int code;
    @Column(length = 16777215 )
    private String content;
    @Column(name = "site_id", nullable = false)
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
