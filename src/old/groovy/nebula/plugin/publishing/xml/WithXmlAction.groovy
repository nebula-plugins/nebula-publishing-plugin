package nebula.plugin.publishing.xml

import java.util.logging.Logger

class WithXmlAction {
    private static final Logger LOGGER = Logger.getLogger(WithXmlAction.getName())

    private Closure closure

    WithXmlAction(Closure closure) {
        assert closure : "Closure has to be set during constructor"
        this.closure = closure
    }

    def execute(Node root) {
        assert root

        closure.delegate = new MissingPropertyToStringDelegate(root)

        // Let creator set strategy
        closure.resolveStrategy = Closure.OWNER_FIRST

        use(NodeEnhancement) {
            closure.call(root)
        }
    }
}


