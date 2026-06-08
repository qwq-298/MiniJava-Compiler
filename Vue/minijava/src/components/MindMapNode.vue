<template>
  <div class="mn-branch">
    <div class="mn-card" :style="cardStyle">
      <span class="mn-icon" :style="{ background: color }">{{ icon }}</span>
      <div class="mn-body">
        <span class="mn-type">{{ node.type }}</span>
        <span class="mn-detail" v-if="detail">{{ detail }}</span>
      </div>
    </div>
    <div class="mn-stem" v-if="hasKids"></div>
    <div class="mn-row" v-if="hasKids">
      <div class="mn-child" v-for="kid in node._kids" :key="kid._id">
        <MindMapNode :node="kid" />
      </div>
    </div>
  </div>
</template>

<script>
const COLORS = {
  Class: '#4EC9B0', Method: '#DCDCAA', Field: '#9CDCFE',
  Assign: '#C586C0', If: '#569CD6', While: '#569CD6',
  For: '#569CD6', Return: '#F44747', Call: '#D7BA7D',
  New: '#4EC9B0', Print: '#569CD6', Program: '#4EC9B0',
}
const ICONS = {
  Class: 'C', Method: 'M', Field: 'F', Assign: '=', If: '?',
  While: '↻', For: '↻', Return: '←', Call: '▶', New: '+', Print: 'P',
}

export default {
  name: 'MindMapNode',
  props: {
    node: { type: Object, required: true },
  },
  computed: {
    hasKids() { return this.node._kids && this.node._kids.length > 0 },
    color()   { return COLORS[this.node.type] || '#808080' },
    icon()    { return ICONS[this.node.type] || (this.node.type || '?')[0] },
    cardStyle() { return { borderColor: this.color } },
    detail() {
      const n = this.node
      const p = []
      if (n.name)              p.push(n.name)
      if (n.dataType)          p.push(': ' + n.dataType)
      if (n.left !== undefined && n.right !== undefined) p.push(n.left + ' = ' + n.right)
      else if (n.right !== undefined) p.push('= ' + n.right)
      if (n.operator)          p.push(n.operator)
      if (n.value !== undefined) p.push(n.value)
      if (n.text && !n.name && !n.left && !n.right) p.push(n.text)
      return p.join('  ')
    }
  }
}
</script>

<style>
.mn-branch { display: flex; flex-direction: column; align-items: center; }
.mn-card {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 16px; border-radius: 10px;
  background: #252526;
  border: 2px solid #555;
  border-left: 5px solid;
  min-width: 120px;
  transition: transform .15s;
  cursor: default;
}
.mn-card:hover { transform: translateY(-1px); }
.mn-icon {
  width: 28px; height: 28px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  color: #1a1a1a; font-size: 13px; font-weight: 800;
  flex-shrink: 0;
}
.mn-body { display: flex; flex-direction: column; gap: 1px; }
.mn-type {
  color: #eee; font-weight: 700; font-size: 14px;
  font-family: 'Cascadia Code', Consolas, monospace;
  white-space: nowrap;
}
.mn-detail {
  color: #999; font-size: 11px;
  font-family: 'Cascadia Code', Consolas, monospace;
  white-space: nowrap; max-width: 200px;
  overflow: hidden; text-overflow: ellipsis;
}
.mn-stem  { width: 2px; height: 18px; background: #555; }
.mn-row   { display: flex; align-items: flex-start; justify-content: center; border-top: 2px solid #555; padding-top: 0; }
.mn-child { display: flex; flex-direction: column; align-items: center; padding: 0 10px; }
.mn-child::before { content: ''; width: 2px; height: 14px; background: #555; }
</style>
