package xyz.erupt.auth.model;

import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_erupt.Tree;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.core.model.BaseModel;

import javax.persistence.*;

/**
 * @author liyuepeng
 * @date 2018-11-22.
 */
@Entity
@Table(name = "E_MENU", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Erupt(
        name = "菜单配置",
        orderBy = "EruptMenu.sort asc",
        tree = @Tree(id = "id", label = "name", pid = "parentMenu.id")
)
@Getter
@Setter
public class EruptMenu extends BaseModel {

    @EruptField(
            views = @View(title = "编码"),
            edit = @Edit(title = "编码", notNull = true)
    )
    private String code;

    @EruptField(
            views = @View(title = "名称"),
            edit = @Edit(
                    title = "名称",
                    notNull = true
            )
    )
    private String name;

    @EruptField(
            edit = @Edit(
                    title = "地址",
                    inputType = @InputType(
                            prefix = {
                                    @VL(value = "/build/table/", label = "表格"),
                                    @VL(value = "/build/tree/", label = "树"),
                                    @VL(value = "/build/tpl/", label = "模板", desc = "使用此功能需要导入tpl模块"),
                                    @VL(value = "/site?&url=", label = "链接"),
                                    @VL(value = "/site?target=blank&url=", label = "新页签"),
                                    @VL(value = "/build/bi/", label = "报表", desc = "此功能需要导入bi模块"),
                                    @VL(value = "/", label = "/", desc = "其他"),
                            }
                    )
            )
    )
    private String path;

    @EruptField(
            edit = @Edit(
                    notNull = true,
                    title = "菜单状态",
                    type = EditType.CHOICE,
                    choiceType = @ChoiceType(
                            vl = {
                                    @VL(value = "1", label = "启用"),
                                    @VL(value = "2", label = "隐藏"),
                                    @VL(value = "3", label = "禁用"),
                            }
                    )
            )
    )
    private Integer status = 1;

    @EruptField(
            edit = @Edit(
                    title = "顺序"
            )
    )
    private Integer sort;


    @EruptField(
            edit = @Edit(
                    title = "图标",
                    desc = "请参考图标库font-awesome"
            )
    )
    private String icon;


    @ManyToOne
    @JoinColumn(name = "PARENT_MENU_ID")
    @EruptField(
            edit = @Edit(
                    title = "上级菜单",
                    type = EditType.REFERENCE_TREE,
                    referenceTreeType = @ReferenceTreeType(pid = "id")
            )
    )
    private EruptMenu parentMenu;

    @Lob
    @EruptField(
            edit = @Edit(
                    title = "菜单参数",
                    type = EditType.TEXTAREA,
                    search = @Search(value = true, vague = true)
            )
    )
    private String param;


    public EruptMenu(String code, String name, String path, Integer status, Integer sort, String icon, EruptMenu parentMenu) {
        this.code = code;
        this.name = name;
        this.path = path;
        this.status = status;
        this.sort = sort;
        this.icon = icon;
        this.parentMenu = parentMenu;
    }

    public EruptMenu() {
    }
}
