# Tarantula Search Bot  
Local search engine Java Sprong Boot application. Add a few websites to the application.yaml file, index them, and search through them! 
## Technologies used  

**Java, Spring Boot, Spring Data Jpa, PostgreSQL, ForkJoinPool, Apache Lucene Morphoilogy**
### Prerequisites:  
1. Java 14 or higher
2. PostgreSQL
3. Maven  
### Installation steps:
1. Clone the project from the repository.
2. On the PostgreSQL Server side, create a schema and a user with the administrative access level to this schema.
3. Locate the application.yml file in the project folder, open it in a text editor, and specify the database connection credentials as the value of the spring.datasource.url configuration parameter.  
It already contains a default value, so you might want to simply modify it. Specify the db user, and the password as well.
Add a few sites you want to index as the values of the sites parameters. You need to specify both the site name and the url.
The port, the application will listen to is also configured here.  
4. Open the src/resources/static/templates/index.html file, locate line 34, and specify your server address following this pattern: protocol://server-address:port/admin
5. Run mvn clean package. This will create a jar file in the target/ folder
6. This jar file can either be run from a CLI/terminal or you can create a .bat file to be able to run it by simply clicking it, e.g. like this: java -jar TarantulaSearchBot-1.0-SNAPSHOT.jar  

***Notes***  
1. If the application crashed due to lack of RAM while indexing, decrease the buffer-size parameter in the application.yml file. Then, rerun mvn clean package.
2. By default, lemmas whose frequency value for the given site is higher then 30% are ignored when performing search and calculating page relevance. This can be fine-tuned via the frequency-threshold parameter in the application.yml file. This is a floating number, so it should be specified in the following format: 0.3f.
3. A trial instance of the app can be found at http://tarantula-search-bot.herokuapp.com/admin/. Note that it runs in a free DB container, so please do not start indexing for the whole website as it will lead to overflowing the free database capacity, and the application will return 500 error status due to inability to execute ddl statements, which are blocked when the db capacity is violated.
