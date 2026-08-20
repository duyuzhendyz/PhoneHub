# 可用工具列表及调用方式

## 一、文件系统工具 (mcp__filesystem__)

所有路径使用 Windows 风格：`C:/PhoneHub/...` 或 `C:\\PhoneHub\\...`

### 1. 读取文本文件
```python
mcp__filesystem__read_text_file(path: str, head: int = None, tail: int = None)
```
- `path`: 要读取的文件路径
- `head`: 如果指定，返回前 N 行
- `tail`: 如果指定，返回后 N 行

### 2. 写入文件
```python
mcp__filesystem__write_file(path: str, content: str)
```
- `path`: 目标文件路径
- `content`: 要写入的字符串内容

### 3. 替换编辑文件
```python
mcp__filesystem__edit_file(path: str, edits: List[dict], dryRun: bool = False)
```
- `edits`: 数组，每个元素包含：
  - `oldText`: 原始要查找的文本
  - `newText`: 替换后的新文本
- `dryRun`: 如果为 true，仅返回 diff 而不实际修改

### 4. 列出目录
```python
mcp__filesystem__list_directory(path: str)
```
返回该目录下的所有文件和子目录。

### 5. 列出目录带大小
```python
mcp__filesystem__list_directory_with_sizes(path: str, sort_by: "name" | "size" = "name")
```
显示文件大小时的排序方式。

### 6. 搜索文件
```python
mcp__filesystem__search_files(path: str, pattern: str, excludePatterns: List[str] = [])
- `pattern`: glob 模式，如 `**/*`、`*.py` 等
- `excludePatterns`: 排除的 glob 模式列表

### 7. 创建目录（支持嵌套）
```python
mcp__filesystem__create_directory(path: str)
```
- 如果目录已存在则静默成功
- 会自动创建所有缺失的父目录

### 8. 移动/重命名文件或目录
```python
mcp__filesystem__move_file(source: str, destination: str)
```
- 支持跨目录移动
- 目标位置若存在则失败

### 9. 获取文件信息
```python
mcp__filesystem__get_file_info(path: str)
```
返回包含大小、创建时间、修改时间、权限等信息。

---

## 二、知识图谱工具 (mcp__memory__)

### 1. 添加观测到实体
```python
mcp__memory__add_observations(entityName: str, contents: List[str])
```

### 2. 创建实体
```python
mcp__memory__create_entities(entities: List[{
    "entityType": string, 
    "name": string, 
    "observations": [string]
}])
```

### 3. 创建关系
```python
mcp__memory__create_relations(relations: List[{
    "from": string, 
    "to": string, 
    "relationType": string
}])
```

### 4. 删除观测
```python
mcp__memory__delete_observations(deletions: List[{
    "entityName": string, 
    "observations": [string]
}])
```

### 5. 删除实体
```python
mcp__memory__delete_entities(entityNames: List[str])
```

### 6. 删除关系
```python
mcp__memory__delete_relations(relations: List[{
    "from": string, 
    "to": string, 
    "relationType": string
}])
```

### 7. 打开节点
```python
mcp__memory__open_nodes(names: List[str])
```

### 8. 搜索节点
```python
mcp__memory__search_nodes(query: string)
```

### 9. 读取完整图谱
```python
mcp__memory__read_graph()
```

---

## 三、多代理协作工具 (multi_agent_v1__)

### 1. 创建子代理
```python
multi_agent_v1__spawn_agent(message: string, model: string = null, reasoning_effort: string = null, service_tier: string = null, items: List[dict] = null, fork_context: boolean = false, agent_type: string = null)
```

### 2. 向子代理发送消息
```python
multi_agent_v1__send_input(target: agent_id, message: string = null, items: List[dict] = null, interrupt: boolean = false)
```

### 3. 等待子代理完成
```python
multi_agent_v1__wait_agent(targets: List[agent_id>, timeout_ms: number = 30000)
```

### 4. 关闭子代理
```python
multi_agent_v1__close_agent(target: agent_id)
```

### 5. 恢复已关闭子代理
```python
multi_agent_v1__resume_agent(id: agent_id)
```

---

## 四、MCP 资源工具

### 1. 列出可用资源
```python
list_mcp_resources(server: string = null, cursor: string = null)
```
- `server`: MCP服务器名，空则列出所有服务器

### 2. 列出资源模板
```python
list_mcp_resource_templates(server: string = null, cursor: string = null)
```

### 3. 读取MCP资源
```python
read_mcp_resource(server: string, uri: string)
```
必须使用从 list_mcp_resources 中获得的真实 URI。

---

## 五、任务管理工具

### 1. 获取当前目标
```python
get_goal()
```

### 2. 创建新目标
```python
create_goal(objective: string, token_budget: int = null)
```

### 3. 更新目标状态
```python
update_goal(status: "pending" | "in_progress" | "completed")
```

---

## 六、其他工具

### 1. 查看图片
```python
view_image(path: string)
```
支持本地图片路径（绝对路径）。

### 2. 执行 PowerShell 命令
```shell_command(command: string, justification: string = "", sandbox_permissions: "use_default" | "require_escalated" = "use_default", timeout_ms: number = 10000, workdir: string = "", prefix_rule: List<string> = [], login: boolean = true)
- `sandbox_permissions`: 设为 `"require_escalated"` 可提权运行
- `prefix_rule`: 用于缓存授权的命令前缀匹配规则

### 3. 查询文档
```query_docs(libraryId: string, query: string)
- 通过 Context7 API 查询最新文档
- libraryId 格式如 `/vercel/next.js/v14.3.0`

### 4. 解析库 ID
```resolve_libraryId(libraryName: string, query: string)
- 将库名称转换为 Context7 兼容的 libraryId
