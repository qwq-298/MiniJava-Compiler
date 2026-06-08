<template>
  <div class="container">
    <header class="header">
      <div class="title">MiniJava Online Compiler</div>
      <div class="actions">
        <div class="setting-wrapper">
          <button class="icon-btn" @click="show" title="设置">
            <svg viewBox="0 0 24 24" width="18" height="18">
              <path fill="currentColor"
                d="M19.14,12.94a7.43,7.43,0,0,0,.05-.94,7.43,7.43,0,0,0-.05-.94l2.11-1.65a.5.5,0,0,0,.12-.65l-2-3.46a.5.5,0,0,0-.6-.22l-2.49,1a7.28,7.28,0,0,0-1.63-.94l-.38-2.65A.5.5,0,0,0,13.8,2H10.2a.5.5,0,0,0-.49.42L9.33,5.07a7.28,7.28,0,0,0-1.63.94l-2.49-1a.5.5,0,0,0-.6.22l-2,3.46a.5.5,0,0,0,.12.65L4.86,11.06a7.43,7.43,0,0,0-.05.94,7.43,7.43,0,0,0,.05.94L2.75,14.59a.5.5,0,0,0-.12.65l2,3.46a.5.5,0,0,0,.6.22l2.49-1a7.28,7.28,0,0,0,1.63.94l.38,2.65a.5.5,0,0,0,.49.42h3.6a.5.5,0,0,0,.49-.42l.38-2.65a7.28,7.28,0,0,0,1.63-.94l2.49,1a.5.5,0,0,0,.6-.22l2-3.46a.5.5,0,0,0-.12-.65ZM12,15.5A3.5,3.5,0,1,1,15.5,12,3.5,3.5,0,0,1,12,15.5Z" />
            </svg>
          </button>
          <div v-if="ifshowsettings" class="settings-panel" ref="settingsPanel">
            <h3>设置</h3>
            <label>字体大小: {{ fontsize }}</label>
            <input type="range" min="10" max="30" v-model="fontsize" />
          </div>
        </div>
        <button class="delete-btn" @click="dele" title="清空">
          <svg viewBox="0 0 24 24" width="18" height="18">
            <path fill="currentColor"
              d="M6 19c0 1.1.9 2 2 2h8a2 2 0 0 0 2-2V7H6v12zm3.5-9h1v8h-1v-8zm4 0h1v8h-1v-8zM15.5 4l-1-1h-5l-1 1H5v2h14V4z" />
          </svg>
        </button>
        <button class="save-btn" @click="saveFile">
          Save
        </button>
        <button class="ast-btn-header" @click="showASTModal" title="查看AST语法树">
          <svg viewBox="0 0 24 24" width="18" height="18">
            <path fill="currentColor" d="M22 11V3h-7v3H9V3H2v8h7V8h2v10h4v3h7v-8h-7v3h-2V8h2v3h7z"/>
          </svg>
          AST
        </button>
        <button class="run-btn" @click="compile" :disabled="isCompiling">
          {{ isCompiling ? 'Running...' : 'Run ▶' }}
        </button>

        <button class="run-all-btn" @click="runAllFiles">
          Run All ▶▶
        </button>

      </div>
    </header>
    <div v-if="ifshowdelete" class="modal-overlay">
      <div class="modal" @click.stop>
        <h3>确认删除</h3>
        <div class="modal-actions">
          <button @click="ifshowdelete = false">取消</button>
          <button class="danger" @click="dele">确认</button>
        </div>
      </div>
    </div>
    <div v-if="showNewFileModal" class="modal-overlay">
      <div class="modal" @click.stop>
        <h3>新建文件</h3>
        <div class="filename-input-wrapper">
          <input v-model="newFileName" placeholder="请输入文件名" class="modal-input filename-input" />
          <span class="file-ext">.java</span>
        </div>
        <div class="modal-actions">
          <button @click="showNewFileModal = false">取消</button>
          <button class="danger" @click="confirmCreateFile">确认</button>
        </div>
      </div>
    </div>
    <main class="main">
      <aside class="sidebar" :style="{ width: sidebarWidth + 'px' }">
        <div class="sidebar-header">
          Files
        </div>
        <button class="new-file-btn" @click="openNewFileModal">
          + New File
        </button>
        <div class="file-list">
          <div v-for="file in files" :key="file.id" class="file-item"
            :class="{ active: currentFile && currentFile.id === file.id }" @click="openFile(file)"
            @contextmenu.prevent="openContextMenu($event, file)">
            <span v-if="dirtyMap[file.id]" class="dirty-dot"></span>
            {{ file.filename }}
          </div>
        </div>
        <label class="upload-btn">
          <svg viewBox="0 0 24 24" width="16" height="16">
            <path fill="currentColor" d="M5 20h14v-2H5m14-9h-4V3H9v6H5l7 7 7-7z" />
          </svg>
          <span>Upload</span>
          <input type="file" accept=".java" @change="uploadFile" hidden />
        </label>
      </aside>
      <div class="divider" @mousedown="startResizeSidebar"></div>
      <section class="panel editor">
        <div class="editor-container" v-show="currentFile">
          <div id="editor" class="monaco"></div>
        </div>
        <div v-show="!currentFile" class="empty-editor">
          <div class="empty-title">
            Welcome to MiniJava Compiler
          </div>
          <div class="empty-desc">
            Open a file or create a new one
          </div>
        </div>
      </section>
      <div class="divider" @mousedown="startResizeConsole"></div>
      <section class="panel output" :style="{ width: consoleWidth + 'px' }">
        <div class="output-title">Console</div>
        <pre class="console">{{ result }}</pre>
      </section>
    </main>

    <div v-if="contextMenuVisible" class="context-menu" :style="{
      top: contextMenuY + 'px',
      left: contextMenuX + 'px'
    }">
      <div class="menu-item danger" @click="deleteFile">
        删除文件
      </div>
    </div>

    <div v-if="showAST" class="ast-modal-overlay" @click.self="closeASTModal">
      <div class="ast-modal">
        <div class="ast-modal-header">
          <span>AST 语法树可视化</span>
          <button class="ast-close-btn" @click="closeASTModal" type="button">✕</button>
        </div>
        <div class="ast-modal-body">
          <ASTViewer v-if="astData" :data="astData" />
          <div v-else-if="astLoading" class="ast-placeholder">正在解析 AST...</div>
          <div v-else-if="astError" class="ast-placeholder ast-placeholder-err">{{ astError }}</div>
          <div v-else class="ast-placeholder">暂无数据</div>
        </div>
      </div>
    </div>

  </div>
</template>

<script>
import axios from 'axios'
import * as monaco from 'monaco-editor'
import ASTViewer from '../components/ASTViewer.vue'
import { fetchAST } from '../api/code.js'
let editorInstance = null;
export default {
  components: { ASTViewer },
  data() {
    return {
      code: "",
      result: "",
      files: [],
      currentFile: null,
      isCompiling: false,
      ifshowsettings: false,
      ifshowdelete: false,
      fontsize: 14,
      showNewFileModal: false,
      newFileName: "",
      UserId: null,
      contextMenuVisible: false,
      contextMenuX: 0,
      contextMenuY: 0,
      contextFile: null,
      draftMap: {},
      sidebarWidth: 220,
      consoleWidth: 350,
      resizingSidebar: false,
      resizingConsole: false,
      dirtyMap:{},
      showAST: false,
      astData: null,
      astLoading: false,
      astError: '',
    }
  },
  async mounted() {
    this.$nextTick(() => {
      editorInstance = monaco.editor.create(
        document.getElementById("editor"),
        {
          value: this.code,
          language: "java",
          theme: "vs-dark",
          fontSize: this.fontsize,
          automaticLayout: true
        }
      )
      editorInstance.onDidChangeModelContent(() => {
        const value = editorInstance.getValue()
        if (this.currentFile) {
          this.draftMap[this.currentFile.id] = value
          localStorage.setItem("draft_" + this.currentFile.id, value)
          this.$set(this.dirtyMap, this.currentFile.id, true)
        }
      })

    })
    document.addEventListener('click', this.handleClickOutside);
    document.addEventListener('click', () => {
      this.contextMenuVisible = false
    })
    await this.loadUserId();
    this.loadFiles();
  },
  beforeUnmount() {
    if (editorInstance) {
      editorInstance.dispose();
    }
    document.removeEventListener('click', this.handleClickOutside);
  },
  methods: {
    async compile() {
      if (this.isCompiling) return;
      this.code = editorInstance.getValue();
      this.isCompiling = true;
      this.result = "Compiling and running...\n";
      try {
        const res = await axios.post(
          "http://localhost:8080/api/run",
          this.code,
          {
            headers: {
              "Content-Type": "text/plain"
            },
            timeout: 10000
          }
        )
        this.result = res.data.result || "Execution completed with no output.";
      } catch (err) {
        console.error(err);
        if (err.code === 'ECONNABORTED') {
          this.result = "Error: Execution Timeout. 你的代码可能包含死循环或后端响应过慢。";
        } else {
          this.result = "Error: " + err.message;
        }
      } finally {
        this.isCompiling = false;
      }
    },
    show() {
      this.ifshowsettings = !this.ifshowsettings;
    },
    handleClickOutside(event) {
      if (!this.ifshowsettings) return
      const panel = this.$refs.settingsPanel
      const button = this.$refs.settingsBtn
      if (
        panel && !panel.contains(event.target) &&
        button && !button.contains(event.target)
      ) {
        this.ifshowsettings = false
      }
    },
    dele() {
      if (editorInstance) {
        editorInstance.setValue("");
      }
      this.result = "";
      this.ifshowdelete = false;
    },
    async loadUserId() {
      const username = localStorage.getItem("username")
      try {
        const userRes = await axios.get(
          "http://localhost:8080/api/user/by-username",
          {
            params: { username }
          }
        )
        this.UserId = userRes.data
        console.log("当前用户ID:", this.UserId)
      } catch (err) {
        console.error(err)
      }
    },
    loadFiles() {
      axios.get(
        "http://localhost:8080/api/file/list",
        {
          params: {
            userId: this.UserId
          }
        }
      )
        .then(res => {
          this.files = res.data
        })
        .catch(err => {
          console.error(err)
        })
    },
    openFile(file) {
      if (this.currentFile?.id === file.id) {
        return
      }
      this.currentFile = file
      this.initEditor()
      if (this.draftMap[file.id] !== undefined) {
        this.$nextTick(() => {
          editorInstance.setValue(
            this.draftMap[file.id]
          )
        })
        return
      }
      axios.get(
        "http://localhost:8080/api/file/open",
        {
          params: { fileId: file.id }
        }
      )
        .then(res => {
          const content = res.data || ""
          this.$nextTick(() => {
            const localDraft = localStorage.getItem("draft_" + file.id)
            if (localDraft !== null) {
              editorInstance.setValue(localDraft)
              this.draftMap[file.id] = localDraft
            } else {
              editorInstance.setValue(content)
              this.draftMap[file.id] = content
            }

          })
        })
        .catch(err => {
          console.error(err)
        })
    },
    createFile() {
      const filename = prompt("请输入文件名")
      if (!filename) return
      axios.post(
        "http://localhost:8080/api/file/save",
        {
          userId: this.UserId,
          filename: filename,
          content: ""
        }
      )
        .then(res => {
          const newFile = res.data
          // 加入文件列表
          this.files.push(newFile)
          // 当前打开文件
          this.currentFile = newFile
          // 清空编辑器
          editorInstance.setValue("")
        })
        .catch(err => {
          console.error(err)
        })
    },
    uploadFile(event) {
      const file = event.target.files[0]
      if (!file) return
      const formData = new FormData()
      formData.append("file", file)
      formData.append(
        "userId",
        this.UserId
      )
      axios.post(
        "http://localhost:8080/api/file/upload",
        formData,
        {
          headers: {
            "Content-Type":
              "multipart/form-data"
          }
        }
      )
        .then(res => {
          const newFile = res.data
          this.files.push(newFile)
        })
        .catch(err => {
          console.error(err)
        })
    },
    saveFile() {
      this.code = editorInstance.getValue()
      // ===== 没有文件 =====
      if (!this.currentFile) {
        const filename = prompt("请输入文件名")
        if (!filename) return
        const username = localStorage.getItem("username")
        axios.post(
          "http://localhost:8080/api/file/save",
          {
            userId: this.UserId,
            filename: filename,
            content: this.code
          }
        )
          .then(res => {
            const newFile = res.data
            this.currentFile = newFile
            this.files.push(newFile)
            alert("保存成功")
          })
          .catch(err => {
            console.error(err)
          })
        return
      }
      // ===== 已有文件 =====
      axios.put(
        `http://localhost:8080/api/file/${this.currentFile.id}`,
        {
          content: this.code
        }
      )
        .then(() => {
          alert("保存成功")
          const id = this.currentFile.id
          this.$set(this.dirtyMap, id, false)
          delete this.draftMap[id]
          localStorage.removeItem("draft_" + id)
        })
        .catch(err => {
          console.error(err)
        })
    },
    openNewFileModal() {
      this.newFileName = ""
      this.showNewFileModal = true
    },
    async confirmCreateFile() {
      let filename = this.newFileName.trim()

      if (!filename.endsWith(".java")) {
        filename += ".java"
      }
      if (!filename) {
        alert("文件名不能为空")
        return
      }
      const username = localStorage.getItem("username")
      try {
        // 1. 先拿 userId（必须 await）
        const userRes = await axios.get(
          "http://localhost:8080/api/user/by-username",
          {
            params: { username }
          }
        )
        const userId = userRes.data
        // 2. 再创建文件
        const fileRes = await axios.post(
          "http://localhost:8080/api/file/save",
          {
            userId: userId,
            filename: filename,
            content: ""
          }
        )
        const newFile = fileRes.data
        // 3. 更新 UI
        this.files.push(newFile)
        this.currentFile = newFile
        editorInstance.setValue("")
        this.showNewFileModal = false
      } catch (err) {
        console.error(err)
      }
    },
    openContextMenu(event, file) {
      this.contextFile = file
      this.contextMenuX = event.clientX
      this.contextMenuY = event.clientY
      this.contextMenuVisible = true
    },
    async deleteFile() {
      if (!this.contextFile) return
      await axios.delete("http://localhost:8080/api/file/delete", {
        params: {
          fileId: this.contextFile.id
        }
      })
      this.files = this.files.filter(
        f => f.id !== this.contextFile.id
      )
      if (this.currentFile?.id === this.contextFile.id) {
        this.currentFile = null
        editorInstance.setValue("")
      }
      this.contextMenuVisible = false
    },
    initEditor() {
      if (editorInstance) return
      this.$nextTick(() => {
        editorInstance = monaco.editor.create(
          document.getElementById("editor"),
          {
            value: "",
            language: "java",
            theme: "vs-dark",
            fontSize: this.fontsize,
            automaticLayout: true
          }
        )
        editorInstance.onDidChangeModelContent(() => {
          if (this.currentFile) {
            this.draftMap[this.currentFile.id] =
              editorInstance.getValue()
          }
        })
      })
    },
    startResizeSidebar() {
      this.resizingSidebar = true
      document.body.style.userSelect = "none"
      document.addEventListener(
        "mousemove",
        this.resizeSidebar
      )
      document.addEventListener(
        "mouseup",
        this.stopResize
      )
    },
    resizeSidebar(e) {
      if (!this.resizingSidebar) return
      const min = 0
      const max = 500
      this.sidebarWidth =
        Math.min(
          Math.max(e.clientX, min),
          max
        )
      this.$nextTick(() => {
        editorInstance?.layout()
      })
    },
    startResizeConsole() {
      this.resizingConsole = true
      document.body.style.userSelect = "none"
      document.addEventListener(
        "mousemove",
        this.resizeConsole
      )
      document.addEventListener(
        "mouseup",
        this.stopResize
      )
    },
    resizeConsole(e) {
      if (!this.resizingConsole) return
      const min = 0
      const max = 800
      this.consoleWidth =
        Math.min(
          Math.max(
            window.innerWidth - e.clientX,
            min
          ),
          max
        )
      this.$nextTick(() => {
        editorInstance?.layout()
      })
    },
    stopResize() {
      this.resizingSidebar = false
      this.resizingConsole = false
      document.body.style.userSelect = ""
      document.removeEventListener(
        "mousemove",
        this.resizeSidebar
      )
      document.removeEventListener(
        "mousemove",
        this.resizeConsole
      )
      document.removeEventListener(
        "mouseup",
        this.stopResize
      )
    },
    async runAllFiles() {
      this.result = "Running all files...\n";
      const payload = this.files.map(f => {
        return {
          filename: f.filename,
          content:
            this.draftMap[f.id] ?? ""
        }
      });
      try {
        const res = await axios.post(
          "http://localhost:8080/api/file/run-all",
          payload
        );
        this.result = res.data.result;
      } catch (err) {
        this.result = "Error: " + err.message;
      }
    },
    showASTModal() {
      if (!editorInstance) return
      const code = editorInstance.getValue()
      if (!code.trim()) {
        this.astError = '编辑器中没有代码'
        this.astData = null
        this.showAST = true
        return
      }
      this.showAST = true
      this.astLoading = true
      this.astError = ''
      this.astData = null
      fetchAST(code)
        .then(res => {
          this.astData = res.data
        })
        .catch(err => {
          console.error(err)
          this.astError = err.response?.data?.message || err.message || '请求失败'
        })
        .finally(() => {
          this.astLoading = false
        })
    },
    closeASTModal() {
      this.showAST = false
    },
  },
  watch: {
    fontsize(newSize) {
      if (editorInstance) {
        editorInstance.updateOptions({
          fontSize: parseInt(newSize, 10)
        })
      }
    }
  }
}

</script>

<style>
.container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  font-family: Arial;
  background: #1e1e1e;
}

.header {
  height: 50px;
  background: #2d2d2d;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 15px;
}

.title {
  font-weight: bold;
}

.run-btn {
  background: #4caf50;
  border: none;
  color: white;
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 4px;
}

.main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-container {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.editor {
  border-right: 1px solid #333;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.monaco {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.output {
  flex-shrink: 0;
  flex: 0 0 auto;
  background: #1e1e1e;
  color: #00ff90;
  max-width: 800px;
}

.output-title {
  padding: 8px;
  border-bottom: 1px solid #333;
  color: #aaa;
}

.console {
  margin: 0;
  padding: 10px;
  white-space: pre-wrap;
  overflow-y: auto;
  flex: 1;
  font-family: monospace;
}

.icon-btn {
  background: transparent;
  border: none;
  font-size: 20px;
  cursor: pointer;
}

.delete-btn {
  background: transparent;
  border: none;
  font-size: 20px;
  cursor: pointer;
}

.settings-panel input {
  width: 100%;
}

.actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.setting-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.settings-panel {
  position: absolute;
  top: 45px;
  left: 50%;
  transform: translateX(-50%);
  width: 200px;
  background: #2d2d2d;
  color: white;
  padding: 10px;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
  z-index: 1000;
}

.settings-panel::before {
  content: "";
  position: absolute;
  top: -10px;
  left: 50%;
  transform: translateX(-50%);
  border-width: 5px;
  border-style: solid;
  border-color: transparent transparent #2d2d2d transparent;
}

/* 背景遮罩 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}

/* 弹窗 */
.modal {
  background: #2d2d2d;
  color: white;
  padding: 20px;
  border-radius: 8px;
  width: 280px;
  box-shadow: 0 0 15px rgba(0, 0, 0, 0.6);
}

/* 按钮区域 */
.modal-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18px;
}

.modal-actions button {
  padding: 6px 14px;
  border: none;
  cursor: pointer;
  border-radius: 8px;
  /* ✅ 圆角 */
  transition: 0.2s;
}

.modal-actions .danger {
  background: #3be53565;
  color: white;
}

.icon-btn:hover {
  background: #3a3a3a;
  color: white;
}

.delete-btn:hover {
  background: #3a3a3a;
  color: white;
}

.sidebar {
  /* width: 220px; */
  background: #252526;
  border-right: 1px solid #333;

  display: flex;
  flex-direction: column;

  flex-shrink: 0;
}

.sidebar-header {
  padding: 10px;
  color: #aaa;
  border-bottom: 1px solid #333;
}

.file-list {
  flex: 1;
  overflow-y: auto;
}

.file-item {
  padding: 10px;
  cursor: pointer;
  color: #ddd;
}

.file-item:hover {
  background: #373737;
}

.file-item.active {
  background: #094771;
}

.new-file-btn {
  margin: 10px;
  padding: 8px;

  border: none;

  background: #3c3c3c;
  color: white;

  cursor: pointer;
}

.save-btn {
  background: #1976d2;
  border: none;
  color: white;
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 4px;
}

.modal-input {
  width: 100%;
  margin-top: 10px;
  padding: 6px;
  border: 1px solid #444;
  border-radius: 8px;
  background: #1e1e1e;
  color: white;
  outline: none;
}

.modal-input:focus {
  border-color: #4caf50;
}

.context-menu {
  position: fixed;
  background: #2d2d2d;
  border: 1px solid #444;
  border-radius: 8px;
  padding: 6px 0;
  min-width: 120px;
  z-index: 9999;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.5);
}

.menu-item {
  padding: 8px 12px;
  cursor: pointer;
  color: #ddd;
  font-size: 13px;
}

.menu-item:hover {
  background: #3a3a3a;
}

.menu-item.danger:hover {
  background: #e53935;
  color: white;
}

.empty-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #888;
  background: #1e1e1e;
}

.empty-title {
  font-size: 20px;
  margin-bottom: 10px;
}

.empty-desc {
  font-size: 14px;
}

.divider {

  width: 5px;

  cursor: col-resize;

  background: #2d2d2d;

  transition: background 0.15s;

  position: relative;

  z-index: 100;
}

.divider:hover {
  background: #007acc;
}

/* 扩大拖拽区域 */
.divider::before {

  content: "";

  position: absolute;

  top: 0;
  bottom: 0;

  left: -2px;
  right: -2px;
}

.upload-btn {

  margin: 10px;

  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;

  padding: 8px 12px;

  background: #3c3c3c;
  color: white;

  border-radius: 6px;

  cursor: pointer;

  transition: 0.2s;

  font-size: 14px;

  user-select: none;
}

.upload-btn:hover {
  background: #4a4a4a;
}

.upload-btn:active {
  transform: scale(0.98);
}

.filename-input-wrapper {
  position: relative;
  width: 100%;
  margin-top: 10px;
}

.filename-input {
  width: 100%;
  padding: 6px 55px 6px 10px;
  border: 1px solid #444;
  border-radius: 8px;
  background: #1e1e1e;
  color: white;
  outline: none;
  box-sizing: border-box;
}

.filename-input:focus {
  border-color: #4caf50;
}

.file-ext {
  position: absolute;
  right: 12px;
  top: 55%;
  transform: translateY(-50%);
  color: #888;
  font-size: 13px;
  pointer-events: none;
}

.run-all-btn {
  background: #ff9800;
  border: none;
  color: white;
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 4px;
}

.run-all-btn:hover {
  background: #fb8c00;
}

.dirty-dot {
  width: 7px;
  height: 7px;
  background: #4caf50;
  /* VSCode 绿色 */
  border-radius: 50%;
  display: inline-block;
  margin-right: 6px;
  position: relative;
  top: -1px;
}

.ast-btn-header {
  background: #7c3aed;
  border: none;
  color: white;
  padding: 6px 10px;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: bold;
  transition: background 0.2s;
}

.ast-btn-header:hover {
  background: #6d28d9;
}

.ast-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 3000;
}

.ast-modal {
  background: #1e1e1e;
  border-radius: 12px;
  width: 90vw;
  height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
  overflow: hidden;
}

.ast-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: #252526;
  color: #ccc;
  font-weight: bold;
  font-size: 16px;
  border-bottom: 1px solid #333;
}

.ast-close-btn {
  background: transparent;
  border: none;
  color: #ccc;
  font-size: 20px;
  cursor: pointer;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: background 0.2s;
}

.ast-close-btn:hover {
  background: #3a3a3a;
  color: #fff;
}

.ast-modal-body {
  flex: 1;
  overflow: hidden;
}

.ast-placeholder {
  color: #666;
  font-size: 15px;
  font-family: monospace;
}
.ast-placeholder-err {
  color: #f44747;
}

</style>
