package nebula.plugin.publishing

class ManifestElementNameGenerator {
    static String elementName(String manifestProperty) {
        manifestProperty.replaceAll(/\.|-/, '_').replaceAll(/\s/, '').trim()
    }
}
