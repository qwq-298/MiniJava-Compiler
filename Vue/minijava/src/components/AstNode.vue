<template>
  <div class="an-row" :style="{ paddingLeft: depth * 20 + 'px' }">
    <div
      class="an-line"
      :class="{ clickable: hasKids }"
      :style="{ borderLeftColor: color }"
      @click="hasKids && $emit('toggle', node._id)"
    >
      <span class="an-arrow">{{ hasKids ? (open ? '▼' : '▶') : '' }}</span>
      <span class="an-chip" :style="{ background: color }">{{ letter }}</span>
      <span class="an-type" :style="{ color: color }">{{ node.type }}</span>
      <span class="an-text" v-if="label">{{ label }}</span>
      <span class="an-count" v-if="hasKids">{{ node._kids.length }}</span>
    </div>
    <div v-if="open && hasKids" class="an-kids">
      <AstNode
        v-for="kid in node._kids"
        :key="kid._id"
        :node="kid"
        :depth="depth + 1"
        :emap="emap"
        @toggle="id => $emit('toggle', id)"
      />
    </div>
  </div>
</template>

<script>
const COLORS = {
  Class:    '#4EC9B0', Method:  '#DCDCAA', Field:   '#9CDCFE',
  Assign:   '#C586C0', If:      '#569CD6', While:   '#569CD6',
  For:      '#569CD6', Return:  '#F44747', Call:    '#D7BA7D',
  New:      '#4EC9B0', Print:   '#569CD6', Program: '#4EC9B0',
}
const ICONS = {
  Class: 'C', Method: 'M', Field: 'F', Assign: '=', If: '?',
  While: '↻', For: '↻', Return: '←', Call: '▶', New: '+', Print: 'P',
}

export default {
  name: 'AstNode',
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 },
    emap: { type: Object, default: () => ({}) },
  },
  emits: ['toggle'],
  computed: {
    open()    { return this.emap[this.node._id] },
    hasKids() { return this.node._kids && this.node._kids.length > 0 },
    color()   { return COLORS[this.node.type] || '#808080' },
    letter()  { return ICONS[this.node.type] || (this.node.type || '?')[0] },
    label() {
      const n = this.node
      const parts = []
      if (n.name)              parts.push(n.name)
      if (n.dataType)          parts.push(': ' + n.dataType)
      if (n.left !== undefined && n.right !== undefined) parts.push(n.left + ' = ' + n.right)
      else if (n.right !== undefined) parts.push('= ' + n.right)
      if (n.operator)          parts.push(n.operator)
      if (n.value !== undefined) parts.push(n.value)
      if (n.text && !n.name && !n.left && !n.right) parts.push(n.text)
      return parts.join('  ')
    }
  }
}
</script>

<style>
.an-row  { overflow: hidden; }
.an-line {
  display: flex; align-items: center; gap: 8px;
  padding: 5px 12px 5px 8px; margin: 1px 0;
  min-height: 32px;
  border-left: 3px solid transparent;
  border-radius: 0 6px 6px 0;
  transition: background .12s, border-color .12s;
  background: #1e1e1e;
}
.an-line.clickable { cursor: pointer; }
.an-line:hover { background: #2a2d2e; }

.an-arrow { width: 16px; flex-shrink: 0; text-align: center; color: #555; font-size: 10px; }

.an-chip {
  width: 26px; height: 26px; border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
  color: #1a1a1a; font-size: 12px; font-weight: 800;
  flex-shrink: 0; letter-spacing: 0.5px;
}

.an-type {
  font-weight: 700; font-size: 13px; white-space: nowrap;
  font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
  letter-spacing: 0.3px;
}

.an-text {
  color: #aaa; font-size: 12px; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis;
  font-family: 'Cascadia Code', Consolas, monospace;
}

.an-count {
  margin-left: auto;
  background: #3c3c3c; color: #aaa; 
  font-size: 10px; font-weight: 600;
  padding: 2px 8px; border-radius: 10px;
  flex-shrink: 0;
}

.an-kids { overflow: hidden; }
</style>
