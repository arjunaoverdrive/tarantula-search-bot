CREATE TABLE IF NOT EXISTS page
(id INT NOT NULL AUTO_INCREMENT,
path TEXT(65535) NOT NULL,
code INT NOT NULL,
content MEDIUMTEXT NOT NULL,
PRIMARY KEY (id), UNIQUE KEY site_path (site_id, path(510) ASC));

CREATE TABLE IF NOT EXISTS field
(id INT NOT NULL AUTO_INCREMENT,
name VARCHAR(255) NOT NULL,
selector VARCHAR(255) NOT NULL,
weight FLOAT NOT NULL,
PRIMARY KEY(id));

INSERT INTO field (name, selector, weight)
    VALUES ('title', 'title', 1.0), ('body', 'body', 0.8);

CREATE TABLE IF NOT EXISTS lemma
(id INT NOT NULL AUTO_INCREMENT,
lemma VARCHAR(255) NOT NULL,
frequency INT NOT NULL,
PRIMARY KEY(id), UNIQUE KEY site_lemma(site_id, lemma(255) ASC));

CREATE TABLE IF NOT EXISTS `index`
(id INT NOT NULL AUTO_INCREMENT,
page_id INT NOT NULL,
lemma_id INT NOT NULL,
`rank` FLOAT NOT NULL,
PRIMARY KEY(id), UNIQUE KEY page_lemma (page_id, lemma_id));

CREATE TABLE IF NOT EXISTS site
(id INT NOT NULL AUTO_INCREMENT,
status ENUM('INDEXING', 'INDEXED', 'FAILED') NOT NULL,
status_time DATETIME NOT NULL,
last_error TEXT,
url VARCHAR(255) NOT NULL,
name VARCHAR(255) NOT NULL,
PRIMARY KEY(id));