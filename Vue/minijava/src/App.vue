<!-- <template>
  <div class="container">

    <header class="header">
      <div class="title">MiniJava Online Compiler</div>
      <div class="actions">
        
        <div class="setting-wrapper">
           <button class="icon-btn" @click="show" title="设置">
            <svg viewBox="0 0 24 24" width="18" height="18">
            <path fill="currentColor"
              d="M19.14,12.94a7.43,7.43,0,0,0,.05-.94,7.43,7.43,0,0,0-.05-.94l2.11-1.65a.5.5,0,0,0,.12-.65l-2-3.46a.5.5,0,0,0-.6-.22l-2.49,1a7.28,7.28,0,0,0-1.63-.94l-.38-2.65A.5.5,0,0,0,13.8,2H10.2a.5.5,0,0,0-.49.42L9.33,5.07a7.28,7.28,0,0,0-1.63.94l-2.49-1a.5.5,0,0,0-.6.22l-2,3.46a.5.5,0,0,0,.12.65L4.86,11.06a7.43,7.43,0,0,0-.05.94,7.43,7.43,0,0,0,.05.94L2.75,14.59a.5.5,0,0,0-.12.65l2,3.46a.5.5,0,0,0,.6.22l2.49-1a7.28,7.28,0,0,0,1.63.94l.38,2.65a.5.5,0,0,0,.49.42h3.6a.5.5,0,0,0,.49-.42l.38-2.65a7.28,7.28,0,0,0,1.63-.94l2.49,1a.5.5,0,0,0,.6-.22l2-3.46a.5.5,0,0,0-.12-.65ZM12,15.5A3.5,3.5,0,1,1,15.5,12,3.5,3.5,0,0,1,12,15.5Z"/>
            </svg>
          </button>
          <div v-if="ifshowsettings" class="settings-panel" ref="settingsPanel">
            <h3>设置</h3>
            <label>字体大小: {{ fontsize }}</label>
            <input
              type="range"
              min="10"
              max="30"
              v-model="fontsize"
            />
          </div>
        </div>
        <button class="delete-btn" @click="dele" title="清空">
        <svg viewBox="0 0 24 24" width="18" height="18">
        <path fill="currentColor"
         d="M6 19c0 1.1.9 2 2 2h8a2 2 0 0 0 2-2V7H6v12zm3.5-9h1v8h-1v-8zm4 0h1v8h-1v-8zM15.5 4l-1-1h-5l-1 1H5v2h14V4z"/>
        </svg>
        </button>
        <button class="run-btn" @click="compile" :disabled="isCompiling">
          {{ isCompiling ? 'Running...' : 'Run ▶' }}
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

    <main class="main">

      <section class="panel editor">
        <div class="editor-container">
        <div id="editor" class="monaco"></div>
        </div> 
      </section>

      <section class="panel output">
        <div class="output-title">Console</div>
        <pre class="console">{{ result }}</pre>
      </section>

    </main>

  </div>
</template>

<script>
import axios from 'axios'
import * as monaco from 'monaco-editor'

let editorInstance = null; 

export default {
  data() {
    return {
       code: "",
       result: "",
       isCompiling: false,
       ifshowsettings: false,
       ifshowdelete:false,
       fontsize:14,
    }
  },
  mounted() {
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
    })
    document.addEventListener('click', this.handleClickOutside);
  },
  beforeDestroy() {
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
    show(){
      this.ifshowsettings = !this.ifshowsettings;
    },
    handleClickOutside(event){
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
    dele(){
      if(editorInstance){
        editorInstance.setValue("");
      }
      this.result = "";
      this.ifshowdelete = false;
    }
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
  overflow:hidden;
}

.editor {
  border-right: 1px solid #333;
  flex:1;
  min-height: 0;
}

.monaco {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.output {
  background: #1e1e1e;
  color: #00ff90;
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

.delete-btn{
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
  box-shadow: 0 4px 12px rgba(0,0,0,0.5);
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
  background: rgba(0,0,0,0.5);
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
  box-shadow: 0 0 15px rgba(0,0,0,0.6);
}

/* 按钮区域 */
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 15px;
}

.modal-actions button {
  padding: 5px 10px;
  border: none;
  cursor: pointer;
}

.modal-actions .danger {
  background: #e53935;
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

</style> 
 -->

<template>
  <router-view />
</template>