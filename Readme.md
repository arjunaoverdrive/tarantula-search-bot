# Tarantula Search Bot  
Local search engine Java Sprong Boot application. Add a few websites to the application.yaml file, index them, and search through them! 
## Technologies used  
**Java, Spring Boot, Spring Data Jpa, MySQL, ForkJoinPool**  
### Prerequisites:  
1. Java 14 or higher
2. MySQL Server v 8
3. Maven  
### Installation steps:
1. Clone the project from the repository.
2. There are some third-party repositories used in this project in the lemmatizer component; all of them are located in the repo/ folder. Add them to the local Maven repository to be able to build the project. For this, open a CLI/ terminal window, navigate to the project folder, and run the mvn deploy:deploy-file command to add each .jar file, e.g., 
        mvn deploy:deploy-file -Durl="file:///repo/dictionary-reader-1.5 dictionary-reader\1.5" -Dfile="dictionary-reader-1.5.jar" -DgroupId=org.apache.lucene.morphology -DartifactId=dictionary-reader -Dpackaging=jar -Dversion=1.5  
Run this command for each jar file in the repo/ folder.
3. On the MySQL Server side, create a schema and a user with administrative access level to this schema. Copy the DDL from the Script.sql and execute to create tables.
4. Locate the application.yml file in the project folder, open it in a text editor, and specify the database connection credentials as the value of the spring.datasource.url configuration parameter.  
It already contains a default value, so you might want to simply modify it. Specify the db user, and the password as well.
Add a few sites you want to index as the values of the sites parameters. You need to specify both the file name and the url.
The port, the application will listen to is also configured here.  
5. Open the src/resources/static/templates/index.html file, locate line 34, and specify your server address like this:<protocol>://<server-address>:<port>/admin
6. Run mvn clean package. This will create a jar file in the target/ folder
7. This jar file can either be run from a CLI/terminal or you can create a .bat file to be able to run it by simply clicking it, e.g. like this: java -jar TarantulaSearchBot-1.0-SNAPSHOT.jar  

***Notes***  
1. If the application crashed due to lack of RAM while indexing, decrease the buffer-size parameter in the application.yml file. Then, rerun mvn clean package.
2. A trial instance of the app can be found at http://tarantula-search-bot.herokuapp.com/admin/
