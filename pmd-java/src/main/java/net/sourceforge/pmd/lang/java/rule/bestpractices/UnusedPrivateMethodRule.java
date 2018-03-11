/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.rule.bestpractices;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclarator;
import net.sourceforge.pmd.lang.java.ast.AccessNode;
import net.sourceforge.pmd.lang.java.ast.TypeNode;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.symboltable.ClassScope;
import net.sourceforge.pmd.lang.java.symboltable.MethodNameDeclaration;
import net.sourceforge.pmd.lang.java.typeresolution.TypeHelper;
import net.sourceforge.pmd.lang.symboltable.NameDeclaration;
import net.sourceforge.pmd.lang.symboltable.NameOccurrence;
import net.sourceforge.pmd.properties.StringMultiProperty;

/**
 * This rule detects private methods, that are not used and can therefore be
 * deleted.
 */
public class UnusedPrivateMethodRule extends AbstractJavaRule {

    private static final StringMultiProperty IGNORED_ANNOTATIONS_DESCRIPTOR
            = StringMultiProperty.named("ignoredAnnotations")
                                 .desc("Fully qualified names of the annotation types that should be ignored by this rule")
                                 .defaultValues("java.lang.Deprecated")
                                 .build();


    public UnusedPrivateMethodRule() {
        definePropertyDescriptor(IGNORED_ANNOTATIONS_DESCRIPTOR);
    }

    /**
     * Visit each method declaration.
     * 
     * @param node
     *            the method declaration
     * @param data
     *            data - rule context
     * @return data
     */
    public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
        if (node.isInterface()) {
            return data;
        }

        Map<MethodNameDeclaration, List<NameOccurrence>> methods = node.getScope().getEnclosingScope(ClassScope.class)
                .getMethodDeclarations();
        for (MethodNameDeclaration mnd : findUnique(methods)) {
            List<NameOccurrence> occs = methods.get(mnd);
            if (!privateAndNotExcluded(mnd) || hasNeglectAnnotation(mnd)) {
                continue;
            }
            if (occs.isEmpty()) {
                addViolation(data, mnd.getNode(), mnd.getImage() + mnd.getParameterDisplaySignature());
            } else {
                if (calledFromOutsideItself(occs, mnd)) {
                    addViolation(data, mnd.getNode(), mnd.getImage() + mnd.getParameterDisplaySignature());
                }

            }
        }
        return data;
    }

    private Set<MethodNameDeclaration> findUnique(Map<MethodNameDeclaration, List<NameOccurrence>> methods) {
        // some rather hideous hackery here
        // to work around the fact that PMD does not yet do full type analysis
        // when it does, delete this
        Set<MethodNameDeclaration> unique = new HashSet<>();
        Set<String> sigs = new HashSet<>();
        for (MethodNameDeclaration mnd : methods.keySet()) {
            String sig = mnd.getImage() + mnd.getParameterCount() + mnd.isVarargs();
            if (!sigs.contains(sig)) {
                unique.add(mnd);
            }
            sigs.add(sig);
        }
        return unique;
    }

    private boolean calledFromOutsideItself(List<NameOccurrence> occs, NameDeclaration mnd) {
        int callsFromOutsideMethod = 0;
        for (NameOccurrence occ : occs) {
            Node occNode = occ.getLocation();
            ASTConstructorDeclaration enclosingConstructor = occNode
                    .getFirstParentOfType(ASTConstructorDeclaration.class);
            if (enclosingConstructor != null) {
                callsFromOutsideMethod++;
                break; // Do we miss unused private constructors here?
            }
            ASTInitializer enclosingInitializer = occNode.getFirstParentOfType(ASTInitializer.class);
            if (enclosingInitializer != null) {
                callsFromOutsideMethod++;
                break;
            }

            ASTMethodDeclaration enclosingMethod = occNode.getFirstParentOfType(ASTMethodDeclaration.class);
            if (enclosingMethod == null || !mnd.getNode().jjtGetParent().equals(enclosingMethod)) {
                callsFromOutsideMethod++;
            }
        }
        return callsFromOutsideMethod == 0;
    }

    private boolean privateAndNotExcluded(NameDeclaration mnd) {
        ASTMethodDeclarator node = (ASTMethodDeclarator) mnd.getNode();
        return ((AccessNode) node.jjtGetParent()).isPrivate() && !node.hasImageEqualTo("readObject")
                && !node.hasImageEqualTo("writeObject") && !node.hasImageEqualTo("readResolve")
                && !node.hasImageEqualTo("writeReplace");
    }

    /**
     * Checks whether the given node is annotated with annotation in the set.
     * The node should be method name declaration.
     *
     * @param node
     *            the node to check
     * @return <code>true</code> if the annotation has been found
     */
    protected boolean hasNeglectAnnotation(MethodNameDeclaration node) {
        Node parent = node.getNode().jjtGetParent();
        List<ASTAnnotation> annotations = parent.findChildrenOfType(ASTAnnotation.class);
        for (ASTAnnotation annotation : annotations) {
            TypeNode n = (TypeNode) annotation.jjtGetChild(0);
            for (String annotationName : getProperty(IGNORED_ANNOTATIONS_DESCRIPTOR)) {
                if (TypeHelper.isA(n, annotationName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
