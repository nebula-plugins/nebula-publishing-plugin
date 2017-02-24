package nebula.plugin.publishing


interface StandardTextValues {
    public static final String CORE_GROUP_BUILD = "build"

    public static final String CORE_TASK_GROOVYDOC = "groovydoc"
    public static final String CORE_TASK_JAVADOC = "javadoc"
    public static final String CORE_TASK_SCALADOC = "scaladoc"
    public static final String CORE_TASK_CLASSES = "classes"


    public static final String TASK_NAME_GROOVYDOC_JAR = "groovydocJar"
    public static final String TASK_DESC_GROOVYDOC_JAR = "Jars the groovydoc files for the project"
    public static final String TASK_NAME_JAVADOC_JAR = "javadocJar"
    public static final String TASK_DESC_JAVADOC_JAR = "Jars the javadoc files for the project"
    public static final String TASK_NAME_SCALADOC_JAR = "scaladocJar"
    public static final String TASK_DESC_SCALADOC_JAR = "Jars the groovydoc files for the project"

    public static final String TASK_NAME_SOURCE_JAR = "sourceJar"
    public static final String TASK_DESC_SOURCE_JAR = "Jars the source files for the project"

    public static final String ARCHIVE_CLASSIFIER_GROOVYDOC = "groovydoc"
    public static final String ARCHIVE_CLASSIFIER_SOURCES = "sources"
    public static final String ARCHIVE_CLASSIFIER_JAVADOC = "javadoc"
    public static final String ARCHIVE_CLASSIFIER_SCALADOC = "scaladoc"

    public static final String EXTENSION_JAR = "jar"
}