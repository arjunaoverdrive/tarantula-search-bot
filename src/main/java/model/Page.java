package model;

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

    public Page() {
    }

    public Page(String path, int code, String content) {
        this.path = path;
        this.code = code;
        this.content = content;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Page)) return false;
        Page page = (Page) o;
        return getCode() == page.getCode() && getPath().equals(page.getPath()) && Objects.equals(getContent(), page.getContent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPath(), getCode(), getContent());
    }

    @Override
    public String toString() {
        return "Page{" +
                "path='" + path + '\'' +
                ", code=" + code +
                '}';
    }
}
