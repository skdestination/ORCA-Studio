package com.litecut.app.timeline

class RenderPass(
    val passId: String,
    val target: RenderTarget
) {
    private val nodes = ArrayList<RenderNode>()

    fun addNode(node: RenderNode) {
        node.target = target
        nodes.add(node)
    }

    fun getNodes(): List<RenderNode> {
        return nodes
    }

    /**
     * Executes all nodes in this rendering pass sequentially.
     */
    fun execute(context: RenderContext, shaderManager: ShaderManager, stats: RenderStatistics) {
        target.bind()
        for (node in nodes) {
            node.draw(context, shaderManager, stats)
        }
        target.unbind()
    }

    fun clear() {
        nodes.clear()
    }
}
