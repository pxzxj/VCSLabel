package com.ponshine;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class SVNLabelDecorator implements ProjectViewNodeDecorator {

    private final Logger LOG = Logger.getInstance(getClass());

    @Override
    public void decorate(ProjectViewNode node, PresentationData data) {
        boolean moduleNode = false;
        if (node instanceof PsiDirectoryNode) {
            PsiDirectoryNode dirNode = (PsiDirectoryNode) node;
            final Object parentValue = dirNode.getParent().getValue();
            moduleNode =  parentValue instanceof Project || parentValue instanceof ModuleGroup;
        }
        if(!moduleNode){
            Color color = node.getColor();
            Color forcedForeground = data.getForcedTextForeground();
            String text = data.getPresentableText();
            if (StringUtil.isEmpty(text)){
                text = node.getName();
            }
            SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(
                    data, forcedForeground != null ? forcedForeground : color, node);
            data.addText(text, simpleTextAttributes);
            if(!(node instanceof PsiDirectoryNode)){
                SVNLabelService labelService = ServiceManager.getService(node.getProject(), SVNLabelService.class);
                labelService.decorateSvnTag(node, data);
            }
        }
    }


    @Override
    public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
        LOG.info("Decorate package dependencies..........");
    }

    private static EditorColorsScheme getScheme() {
        return EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
    }

    private SimpleTextAttributes getSimpleTextAttributes(@NotNull PresentationData presentation, Color color, @NotNull Object node) {
        SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(presentation, getScheme());

        return addColorToSimpleTextAttributes(simpleTextAttributes, color);
    }

    private static SimpleTextAttributes getSimpleTextAttributes(@Nullable final ItemPresentation presentation,
                                                                @NotNull EditorColorsScheme colorsScheme)
    {
        if (presentation instanceof ColoredItemPresentation) {
            final TextAttributesKey textAttributesKey = ((ColoredItemPresentation) presentation).getTextAttributesKey();
            if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
            final TextAttributes textAttributes = colorsScheme.getAttributes(textAttributesKey);
            return textAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(textAttributes);
        }
        return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    private static SimpleTextAttributes addColorToSimpleTextAttributes(SimpleTextAttributes simpleTextAttributes, Color color) {
        if (color != null) {
            final TextAttributes textAttributes = simpleTextAttributes.toTextAttributes();
            textAttributes.setForegroundColor(color);
            simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
        }
        return simpleTextAttributes;
    }
}
