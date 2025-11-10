package com.aether.aether_backend.repository;

// 【导入 "弹药"】
import com.aether.aether_backend.domain.KnowledgeAtom; // <-- 导入我们的"实体"
import org.springframework.data.jpa.repository.JpaRepository; // <-- 导入"JPA神经索"核心

/**
 * "神经索" (DAO层 / Repository)
 *
 * CTO标准：我们"不"写实现。
 * 我们"命令" Spring Data JPA："请"自动"实现"一个"管理" KnowledgeAtom "实体"的"仓库"，
 * 这个"实体"的"主键(ID)"是 Long 类型。
 *
 * Spring Data JPA 会"自动"为我们提供：
 * - save() (增/改)
 * - findById() (查)
 * - findAll() (查所有)
 * - deleteById() (删)
 * - ...以及"更多"
 */
public interface KnowledgeAtomRepository extends JpaRepository<KnowledgeAtom, Long> {

    // 我们"现在"什么都不用加。
    // 未来(寒假)，当我们需要"定制"查询时 (比如 "按ContentType查找")，
    // 我们才会在这里 "定义" "新" 的方法。

}