package org.springframework.roo.classpath.itd;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.roo.classpath.details.AnnotationMetadataUtils;
import org.springframework.roo.classpath.details.ConstructorMetadata;
import org.springframework.roo.classpath.details.DeclaredFieldAnnotationDetails;
import org.springframework.roo.classpath.details.DeclaredMethodAnnotationDetails;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.ItdTypeDetails;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.model.ImportRegistrationResolver;
import org.springframework.roo.model.ImportRegistrationResolverImpl;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.support.util.Assert;

/**
 * A simple way of producing an inter-type declaration source file.
 * 
 * @author Ben Alex
 * @author Stefan Schmidt
 * @since 1.0
 *
 */
public class ItdSourceFileComposer {
	
	private int indentLevel = 0;
	
	private JavaType introductionTo;
	private StringBuilder pw = new StringBuilder();
	private boolean content;
	private ItdTypeDetails itdTypeDetails;
	private ImportRegistrationResolver resolver;
	private JavaType aspect;

	/**
	 * Constructs an {@link ItdSourceFileComposer} containing the members that were requested in
	 * the passed object.
	 * 
	 * @param itdTypeDetails to construct (required)
	 */
	public ItdSourceFileComposer(ItdTypeDetails itdTypeDetails) {
		Assert.notNull(itdTypeDetails, "ITD type details required");

		this.itdTypeDetails = itdTypeDetails;
		Assert.notNull(itdTypeDetails.getName(), "Introduction to is required");
		this.introductionTo = itdTypeDetails.getName();
		
		this.aspect = itdTypeDetails.getAspect();

		// Create my own resolver, so we can add items to it as we process
		resolver = new ImportRegistrationResolverImpl(itdTypeDetails.getAspect().getPackage());
		
		for (JavaType registeredImport : itdTypeDetails.getRegisteredImports()) {
			// Do a sanity check in case the user misused it
			if (resolver.isAdditionLegal(registeredImport)) {
				resolver.addImport(registeredImport);
			}
		}

		appendTypeDeclaration();
		appendExtendsTypes();
		appendImplementsTypes();
		appendTypeAnnotations();
		appendFieldAnnotations();
		appendMethodAnnotations();
		appendFields();
		appendConstructors();
		appendMethods();
		appendTerminator();
		
		// Now prepend the package declaration and any imports
		// We need to do this ** at the end ** so we can ensure our compilation unit imports are correct, as they're built as we traverse over the other members
		prependCompilationUnitDetails();
	}
	
	private void prependCompilationUnitDetails() {
		StringBuilder topOfFile = new StringBuilder();

		// Note we're directly interacting with the top of file string builder
		if (!aspect.isDefaultPackage()) {
			topOfFile.append("package " + aspect.getPackage().getFullyQualifiedPackageName() + ";").append(getNewLine());
			topOfFile.append(getNewLine());
		}
		
		// Ordered to ensure consistency of output
		SortedSet<JavaType> types = new TreeSet<JavaType>();
		types.addAll(resolver.getRegisteredImports());
		if (types.size() > 0) {
			for (JavaType importType : types) {
				topOfFile.append("import " + importType.getFullyQualifiedTypeName() + ";").append(getNewLine());
			}
			
			topOfFile.append(getNewLine());
		}
		
		// Now append the normal file to the bottom
		topOfFile.append(pw.toString());
		
		// Replace the old writer with out new writer
		this.pw = topOfFile;
	}
	
	private void appendTypeDeclaration() {
		Assert.isTrue(introductionTo.getPackage().equals(aspect.getPackage()), "Aspect and introduction must be in identical packages");
		
		this.appendIndent();
		if (itdTypeDetails.isPrivilegedAspect()) {
			this.append("privileged ");
		}
		this.append("aspect " + aspect.getSimpleTypeName() + " {");
		this.newLine(false);
		this.indent();
		this.newLine();

		// Set to false, as it was set true during the above operations
		content = false;
	}

	private void outputAnnotation(AnnotationMetadata annotation) {
		this.append(AnnotationMetadataUtils.toSourceForm(annotation, resolver));
	}
	
	private void appendTypeAnnotations() {
		List<? extends AnnotationMetadata> typeAnnotations = itdTypeDetails.getTypeAnnotations();
		if (typeAnnotations == null || typeAnnotations.size() == 0) {
			return;
		}
		
		content = true;
		
		for (AnnotationMetadata typeAnnotation : typeAnnotations) {
			this.appendIndent();
			this.append("declare @type: ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(": ");
			outputAnnotation(typeAnnotation);
			this.append(";");
			this.newLine(false);
			this.newLine();
		}
	}
	
	private void appendFieldAnnotations() {
		List<DeclaredFieldAnnotationDetails> fieldAnnotations = itdTypeDetails.getFieldAnnotations();
		if (fieldAnnotations == null || fieldAnnotations.size() == 0) {
			return;
		}
		
		content = true;
		
		for (DeclaredFieldAnnotationDetails fieldDetails : fieldAnnotations) {	
			this.appendIndent();
			this.append("declare @field: * ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(".");
			this.append(fieldDetails.getFieldMetadata().getFieldName().getSymbolName());
			this.append(": ");
			outputAnnotation(fieldDetails.getFieldAnnotation());
			this.append(";");
			this.newLine(false);
			this.newLine();
		}
	}
	
	private void appendMethodAnnotations() {
		List<DeclaredMethodAnnotationDetails> methodAnnotations = itdTypeDetails.getMethodAnnotations();
		if (methodAnnotations == null || methodAnnotations.size() == 0) {
			return;
		}
		
		content = true;
		
		for (DeclaredMethodAnnotationDetails methodDetails : methodAnnotations) {	
			this.appendIndent();
			this.append("declare @method: ");
			this.append(Modifier.toString(methodDetails.getMethodMetadata().getModifier()));
			this.append(" ");
			this.append(methodDetails.getMethodMetadata().getReturnType().getNameIncludingTypeParameters());
			this.append(" ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(".");
			this.append(methodDetails.getMethodMetadata().getMethodName().getSymbolName());
			this.append("(");
			for (int i = 0; i < methodDetails.getMethodMetadata().getParameterTypes().size(); i++) {
				this.append(methodDetails.getMethodMetadata().getParameterTypes().get(i).getJavaType().getNameIncludingTypeParameters(false, resolver));
				if (i != methodDetails.getMethodMetadata().getParameterTypes().size() - 1) {
					this.append(",");
				}
			}
			this.append("): ");
			outputAnnotation(methodDetails.getMethodAnnotation());
			this.append(";");
			this.newLine(false);
			this.newLine();
		}
	}

	private void appendExtendsTypes() {
		List<JavaType> extendsTypes = itdTypeDetails.getExtendsTypes();
		if (extendsTypes == null || extendsTypes.size() == 0) {
			return;
		}
		
		content = true;
		
		for (JavaType extendsType : extendsTypes) {
			this.appendIndent();
			this.append("declare parents: ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(" extends ");
			if (resolver.isFullyQualifiedFormRequiredAfterAutoImport(extendsType)) {
				this.append(extendsType.getNameIncludingTypeParameters());
			} else {
				this.append(extendsType.getNameIncludingTypeParameters(false, resolver));
			}
			this.append(";");
			this.newLine(false);
			this.newLine();
		}
	}

	private void appendImplementsTypes() {
		List<JavaType> implementsTypes = itdTypeDetails.getImplementsTypes();
		
		if (implementsTypes == null || implementsTypes.size() == 0) {
			return;
		}
		
		content = true;
		
		for (JavaType extendsType : implementsTypes) {
			this.appendIndent();
			this.append("declare parents: ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(" implements ");
			if (resolver.isFullyQualifiedFormRequiredAfterAutoImport(extendsType)) {
				this.append(extendsType.getNameIncludingTypeParameters());
			} else {
				this.append(extendsType.getNameIncludingTypeParameters(false, resolver));
			}
			this.append(";");
			this.newLine(false);
			this.newLine();
		}
	}

	private void appendConstructors() {
		List<? extends ConstructorMetadata> constructors = itdTypeDetails.getDeclaredConstructors();
		if (constructors == null || constructors.size() == 0) {
			return;
		}
		content = true;
		for (ConstructorMetadata constructor : constructors) {
			Assert.isTrue(constructor.getParameterTypes().size() == constructor.getParameterNames().size(), "Mismatched parameter names against parameter types");
			
			// Append annotations
			for (AnnotationMetadata annotation : constructor.getAnnotations()) {
				this.appendIndent();
				outputAnnotation(annotation);
				this.newLine(false);
			}
			
			// Append "<modifier> <TargetOfIntroduction>.new" portion
			this.appendIndent();
			if (constructor.getModifier() != 0) {
				this.append(Modifier.toString(constructor.getModifier()));
				this.append(" ");
			}
			this.append(introductionTo.getSimpleTypeName());
			this.append(".");
			this.append("new");

			// Append parameter types and names
			this.append("(");
			List<AnnotatedJavaType> paramTypes = constructor.getParameterTypes();
			List<JavaSymbolName> paramNames = constructor.getParameterNames();
			for (int i = 0 ; i < paramTypes.size(); i++) {
				AnnotatedJavaType paramType = paramTypes.get(i);
				JavaSymbolName paramName = paramNames.get(i);
				for (AnnotationMetadata methodParameterAnnotation : paramType.getAnnotations()) {
					this.append(AnnotationMetadataUtils.toSourceForm(methodParameterAnnotation));
					this.append(" ");
				}
				this.append(paramType.getJavaType().getNameIncludingTypeParameters(false, resolver));
				this.append(" ");
				this.append(paramName.getSymbolName());
				if (i < paramTypes.size() - 1) {
					this.append(", ");
				}
			}
			this.append(") {");
			this.newLine(false);
			this.indent();

			// Add body
			this.append(constructor.getBody());
			this.indentRemove();
			this.appendFormalLine("}");
			this.newLine(false);
		}
	}
	
	private void appendMethods() {
		List<? extends MethodMetadata> methods = itdTypeDetails.getDeclaredMethods();
		if (methods == null || methods.size() == 0) {
			return;
		}
		content = true;
		for (MethodMetadata method : methods) {
			Assert.isTrue(method.getParameterTypes().size() == method.getParameterNames().size(), "Mismatched parameter names against parameter types");
			
			// Append annotations
			for (AnnotationMetadata annotation : method.getAnnotations()) {
				this.appendIndent();
				outputAnnotation(annotation);
				this.newLine(false);
			}
			
			// Append "<modifier> <returntype> <methodName>" portion
			this.appendIndent();
			if (method.getModifier() != 0) {
				this.append(Modifier.toString(method.getModifier()));
				this.append(" ");
			}

			// return type
			boolean staticMethod = Modifier.isStatic(method.getModifier());
			this.append(method.getReturnType().getNameIncludingTypeParameters(staticMethod, resolver));
			this.append(" ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(".");
			this.append(method.getMethodName().getSymbolName());

			// Append parameter types and names
			this.append("(");
			List<AnnotatedJavaType> paramTypes = method.getParameterTypes();
			List<JavaSymbolName> paramNames = method.getParameterNames();
			for (int i = 0 ; i < paramTypes.size(); i++) {
				AnnotatedJavaType paramType = paramTypes.get(i);
				JavaSymbolName paramName = paramNames.get(i);
				for (AnnotationMetadata methodParameterAnnotation : paramType.getAnnotations()) {
					outputAnnotation(methodParameterAnnotation);
					this.append(" ");
				}
				this.append(paramType.getJavaType().getNameIncludingTypeParameters(false, resolver));
				this.append(" ");
				this.append(paramName.getSymbolName());
				if (i < paramTypes.size() - 1) {
					this.append(", ");
				}
			}
			
			// add exceptions to be thrown
			List<JavaType> throwsTypes = method.getThrowsTypes();
			if (throwsTypes.size() > 0) {
				this.append(") throws ");
				for (int i = 0; i < throwsTypes.size(); i++) {
					this.append(throwsTypes.get(i).getNameIncludingTypeParameters(false, resolver));
					if (throwsTypes.size() > (i+1)) {
						this.append(", ");
					}
				}
				this.append(" {");
			} else {
				this.append(") {");
			}
			
			this.newLine(false);
			this.indent();

			// Add body
			this.append(method.getBody());
			this.indentRemove();
			this.appendFormalLine("}");
			this.newLine();
		}
	}
	
	private void appendFields() {
		List<? extends FieldMetadata> fields = itdTypeDetails.getDeclaredFields();
		if (fields == null || fields.size() == 0) {
			return;
		}
		content = true;
		for (FieldMetadata field : fields) {
			
			// Append annotations
			for (AnnotationMetadata annotation : field.getAnnotations()) {
				this.appendIndent();
				outputAnnotation(annotation);
				this.newLine(false);
			}
			
			// Append "<modifier> <fieldtype> <fieldName>" portion
			this.appendIndent();
			if (field.getModifier() != 0) {
				this.append(Modifier.toString(field.getModifier()));
				this.append(" ");
			}
			this.append(field.getFieldType().getNameIncludingTypeParameters(false, resolver));
			this.append(" ");
			this.append(introductionTo.getSimpleTypeName());
			this.append(".");
			this.append(field.getFieldName().getSymbolName());

			// Append initializer, if present
			if (field.getFieldInitializer() != null) {
				this.append(" = ");
				this.append(field.getFieldInitializer());
			}
			
			// Complete the field declaration
			this.append(";");
			this.newLine(false);
			this.newLine();
		}
	}

	/**
	 * Increases the indent by one level.
	 */
	private ItdSourceFileComposer indent() {
		indentLevel++;
		return this;
	}

	/**
	 * Decreases the indent by one level.
	 */
	private ItdSourceFileComposer indentRemove() {
		indentLevel--;
		return this;
	}

	/**
	 * Prints a blank line, ensuring any indent is included before doing so.
	 */
	private ItdSourceFileComposer newLine() {
		return newLine(true);
	}
	
	/**
	 * Prints a blank line, ensuring any indent is included before doing so.
	 */
	private ItdSourceFileComposer newLine(boolean indent) {
		if (indent) appendIndent();
        // We use \n for consistency with JavaParser's DumpVisitor, which always uses \n
		pw.append(getNewLine());
		//pw.append(System.getProperty("line.separator"));
		return this;
	}

	private String getNewLine() {
        // We use \n for consistency with JavaParser's DumpVisitor, which always uses \n
		return ("\n");
	}
	
	/**
	 * Prints the message, WITHOUT ANY INDENTATION.
	 */
	private ItdSourceFileComposer append(String message) {
		if (message != null && !"".equals(message)) {
			pw.append(message);
			content = true;
		}
		return this;
	}

	/**
	 * Prints the message, after adding indents and returns to a new line. This is the most commonly used method.
	 */
	private ItdSourceFileComposer appendFormalLine(String message) {
		appendIndent();
		if (message != null && !"".equals(message)) {
			pw.append(message);
			content = true;
		}
		return newLine(false);
	}

	/**
	 * Prints the relevant number of indents.
	 */
	private ItdSourceFileComposer appendIndent() {
		for (int i = 0 ; i < indentLevel; i++) {
			pw.append("    ");
		}
		return this;
	}
	
	private void appendTerminator() {
		Assert.isTrue(this.indentLevel == 1, "Indent level must be 1 (not " + indentLevel + ") to conclude!");
		this.indentRemove();
		
		// Ensure we present the content flag, as it will be set true during the formal line append
		boolean contentBefore = content;
		this.appendFormalLine("}");
		content = contentBefore;
		
	}
	
	public String getOutput() {
		return pw.toString();
	}

	/**
	 * Indicates whether any content was added to the ITD, aside from the formal ITD declaration.
	 * 
	 * @return true if there is actual content in the ITD, false otherwise
	 */
	public boolean isContent() {
		return content;
	}
}
