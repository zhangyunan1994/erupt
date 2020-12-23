package xyz.erupt.annotation.sub_erupt;

import xyz.erupt.annotation.config.Comment;
import xyz.erupt.annotation.fun.OperationHandler;

import java.beans.Transient;

/**
 * 使用一列或者多列的数据执行特定代码
 *
 * @author liyuepeng
 * @date 2018-10-09.
 */
public @interface RowOperation {

    String code();

    String title();

    String tip() default "";

    @Comment("图标请参考Font Awesome")
    String icon() default "fa fa-ravelry";

    Mode mode() default Mode.MULTI;

    Type type() default Type.ERUPT;

    @Comment("控制按钮显示与隐藏（JS表达式），变量：item 获取整行数据")
    String ifExpr() default "";

    @Comment("type为tpl时可用，可在模板中使用rows变量，可获取选中行的数据")
    @Transient
    Tpl tpl() default @Tpl(path = "");

    @Comment("按钮提交时，需要填写的表单信息")
    Class<?> eruptClass() default void.class;

    @Transient
    @Comment("该配置可在operationHandler中获取")
    String[] operationParam() default {};

    @Transient
    @Comment("type为ERUPT时可用，事件处理类")
    Class<? extends OperationHandler> operationHandler() default OperationHandler.class;

    enum Mode {
        @Comment("依赖单行数据")
        SINGLE,
        @Comment("依赖多行数据")
        MULTI,
        @Comment("无需依赖数据")
        BUTTON
    }

    enum Type {
        @Comment("通过erupt表单渲染，operationHandler进行逻辑处理")
        ERUPT,
        @Comment("通过自定义模板渲染")
        TPL
    }
}
