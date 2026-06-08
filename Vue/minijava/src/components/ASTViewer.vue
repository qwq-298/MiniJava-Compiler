<template>
  <div class="av-root">
    <div class="av-bar">
      <span>AST 语法树</span>
      <div class="av-acts">
        <button :class="{ on: mode==='list' }" @click="mode='list'" title="列表视图">☰</button>
        <button :class="{ on: mode==='map' }" @click="mode='map'" title="思维导图">⊞</button>
        <span class="av-sep"></span>
        <button @click="zoomIn" title="放大">+</button>
        <span class="av-zoom-label">{{ pct }}%</span>
        <button @click="zoomOut" title="缩小">−</button>
        <button @click="zoomReset" title="重置">1:1</button>
        <span class="av-sep"></span>
        <button v-if="mode==='list'" @click="expandAll" title="全部展开">⊞</button>
        <button v-if="mode==='list'" @click="collapseAll" title="全部折叠">⊟</button>
      </div>
    </div>
    <div class="av-body" v-if="root">
      <div class="av-zoom" :style="{ transform: 'scale(' + zoom + ')', transformOrigin: 'top left' }">
        <AstNode v-if="mode==='list'" :node="root" :depth="0" :emap="emap" @toggle="onToggle" />
        <div v-else class="av-map-scroll"><MindMapNode :node="root" /></div>
      </div>
    </div>
    <div class="av-body av-err" v-else-if="err">{{ err }}</div>
    <div class="av-body av-emp" v-else>暂无 AST 数据</div>
  </div>
</template>

<script>
import AstNode from './AstNode.vue'
import MindMapNode from './MindMapNode.vue'

export default {
  name: 'ASTViewer',
  components: { AstNode, MindMapNode },
  props: { data: { type: Object, default: null } },
  data() { return { root: null, emap: {}, err: '', _n: 0, mode: 'list', zoom: 1 } },
  computed: {
    pct() { return Math.round(this.zoom * 100) }
  },
  watch: {
    data: { handler(v) { if (v) this.build(v); else this.root = null }, immediate: true, deep: true }
  },
  errorCaptured(e) { this.err = '渲染出错: ' + e.message; return false },
  methods: {
    build(raw) {
      this.err = ''; this._n = 0; this.emap = {}
      if (!raw || !raw.type) { this.err = '无效的 AST 数据'; this.root = null; return }
      try {
        this.root = this.walk(raw)
        this.autoExpand(this.root, 3)
      } catch (e) { this.err = '解析出错: ' + e.message; this.root = null }
    },
    walk(node) {
      if (!node) return null
      const n = { _id: this._n++, type: node.type, _kids: [], name: node.name }
      for (const k of ['dataType','left','right','operator','value','text']) {
        if (node[k] !== undefined) n[k] = node[k]
      }
      const kids = []
      if (Array.isArray(node.children)) kids.push(...node.children)
      if (Array.isArray(node.body))     kids.push(...node.body)
      for (const c of kids) {
        const child = this.walk(c)
        if (child) n._kids.push(child)
      }
      return n
    },
    autoExpand(node, maxD, d = 0) {
      if (!node) return
      this.emap = { ...this.emap, [node._id]: true }
      if (d < maxD && node._kids) node._kids.forEach(c => this.autoExpand(c, maxD, d + 1))
    },
    onToggle(id) { this.emap = { ...this.emap, [id]: !this.emap[id] } },
    expandAll()   { const m = {}; this._expand(this.root, m); this.emap = m },
    collapseAll() { const m = {}; this._collapse(this.root, m); this.emap = m },
    _expand(node, m)   { if (!node) return; m[node._id] = true; node._kids?.forEach(c => this._expand(c, m)) },
    _collapse(node, m) { if (!node) return; m[node._id] = false; node._kids?.forEach(c => this._collapse(c, m)) },
    zoomIn()    { this.zoom = Math.min(3, +(this.zoom * 1.25).toFixed(2)) },
    zoomOut()   { this.zoom = Math.max(0.25, +(this.zoom / 1.25).toFixed(2)) },
    zoomReset() { this.zoom = 1 },
  }
}
</script>

<style scoped>
.av-root { display: flex; flex-direction: column; height: 100%; background: #1e1e1e; border-radius: 8px; overflow: hidden; }
.av-bar  { display: flex; justify-content: space-between; align-items: center; padding: 8px 14px; background: #252526; border-bottom: 1px solid #333; flex-shrink: 0; color: #ccc; font-size: 13px; font-weight: 600; font-family: 'Segoe UI', system-ui, sans-serif; }
.av-acts { display: flex; gap: 4px; align-items: center; }
.av-acts button { width: 26px; height: 26px; border: none; background: #3c3c3c; color: #ccc; border-radius: 4px; cursor: pointer; font-size: 13px; transition: .15s; }
.av-acts button:hover { background: #505050; }
.av-acts button.on { background: #0e639c; color: #fff; }
.av-sep { width: 1px; height: 18px; background: #444; margin: 0 4px; }
.av-zoom-label { color: #999; font-size: 11px; font-weight: 400; min-width: 36px; text-align: center; }
.av-body { flex: 1; overflow: auto; font-size: 14px; }
.av-zoom { display: inline-block; min-width: 100%; padding: 8px 0; }
.av-map-scroll { padding: 24px; overflow: auto; }
.av-err  { display: flex; align-items: center; justify-content: center; color: #f44747; font-family: monospace; }
.av-emp  { display: flex; align-items: center; justify-content: center; color: #666; font-family: monospace; }
</style>
