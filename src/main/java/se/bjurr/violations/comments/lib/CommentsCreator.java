package se.bjurr.violations.comments.lib;

import static se.bjurr.violations.lib.util.Optional.absent;
import static se.bjurr.violations.lib.util.Optional.fromNullable;
import static se.bjurr.violations.lib.util.Utils.checkNotNull;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.bjurr.violations.comments.lib.model.ChangedFile;
import se.bjurr.violations.comments.lib.model.Comment;
import se.bjurr.violations.comments.lib.model.CommentsProvider;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.util.Optional;

public class CommentsCreator {
  private static final String DEFAULT_VIOLATION_TEMPLATE_MUSTACH =
      "default-violation-template.mustach";
  public static final String FINGERPRINT =
      "<this is a auto generated comment from violation-comments-lib F7F8ASD8123FSDF>";
  private static final Logger LOG = LoggerFactory.getLogger(CommentsCreator.class);
  public static final String FINGERPRINT_ACC = "<ACCUMULATED-VIOLATIONS>";

  public static void createComments(
      final CommentsProvider commentsProvider,
      final List<Violation> violations,
      final Integer maxCommentSize) {
    final CommentsCreator commentsCreator =
        new CommentsCreator(commentsProvider, violations, maxCommentSize);
    commentsCreator.createComments();
  }

  private final CommentsProvider commentsProvider;
  private final List<ChangedFile> files;

  private final Integer maxCommentSize;
  private final List<Violation> violations;

  CommentsCreator(
      final CommentsProvider commentsProvider,
      final List<Violation> violations,
      final Integer maxCommentSize) {
    checkNotNull(violations, "violations");
    checkNotNull(commentsProvider, "commentsProvider");
    this.commentsProvider = commentsProvider;
    LOG.debug(violations.size() + " violations.");
    files = commentsProvider.getFiles();
    this.violations = filterChanged(violations);
    this.maxCommentSize = maxCommentSize;
  }

  public void createComments() {
    if (commentsProvider.shouldCreateCommentWithAllSingleFileComments()) {
      createCommentWithAllSingleFileComments();
    }
    if (commentsProvider.shouldCreateSingleFileComment()) {
      createSingleFileComments();
    }
  }

  private void createCommentWithAllSingleFileComments() {
    final String s = getAccumulatedComments();
    LOG.debug(
        "Asking "
            + commentsProvider.getClass().getSimpleName()
            + " to create comment with all single file comments.");
    List<Comment> oldComments = commentsProvider.getComments();
    oldComments = filterCommentsWithContent(oldComments, FINGERPRINT);
    oldComments = filterCommentsWithContent(oldComments, FINGERPRINT_ACC);
    final List<Comment> theNewComment = filterCommentsWithContent(oldComments, s);
    final boolean commentAlreadyExists = !theNewComment.isEmpty();
    oldComments.removeAll(theNewComment);

    if (!commentsProvider.shouldKeepOldComments()) {
      commentsProvider.removeComments(oldComments);
    }

    if (violations.isEmpty()) {
      return;
    }

    if (!commentAlreadyExists) {
      commentsProvider.createCommentWithAllSingleFileComments(s);
    }
  }

  String getAccumulatedComments() {
    StringBuilder sb = new StringBuilder();
    sb.append("Found " + violations.size() + " violations:\n\n");
    for (final Violation violation : violations) {
      final Optional<ChangedFile> changedFile = getFile(violation);
      final String singleFileCommentContent =
          createSingleFileCommentContent(changedFile.get(), violation);
      if (sb.length() + singleFileCommentContent.length() >= maxCommentSize) {
        LOG.debug(
            "Asking "
                + commentsProvider.getClass().getSimpleName()
                + " to create comment with a subset of all single file comments.");
        commentsProvider.createCommentWithAllSingleFileComments(sb.toString());
        sb = new StringBuilder();
      }
      sb.append(singleFileCommentContent + "\n");
    }
    sb.append(" *" + FINGERPRINT_ACC + "*");
    return sb.toString();
  }

  String createSingleFileCommentContent(final ChangedFile changedFile, final Violation violation) {
    final MustacheFactory mf = new DefaultMustacheFactory();
    Reader templateReader = null;
    final Optional<String> commentTemplateOpt = commentsProvider.findCommentTemplate();
    if (commentTemplateOpt.isPresent() && !commentTemplateOpt.get().isEmpty()) {
      templateReader = new StringReader(commentTemplateOpt.get());
    } else {
      templateReader = mf.getReader(DEFAULT_VIOLATION_TEMPLATE_MUSTACH);
    }
    final Mustache mustache = mf.compile(templateReader, "Violation Template");
    final Writer writer = new StringWriter();
    final Map<String, Object> context = new HashMap<>();
    context.put("violation", violation);
    context.put("changedFile", changedFile);
    final List<Object> scopes = new ArrayList<>();
    scopes.add(context);
    mustache.execute(writer, scopes);
    return writer.toString() + "\n\n*" + FINGERPRINT + "* *<" + identifier(violation) + ">*";
  }

  private String identifier(final Violation violation) {
    // The letter makes it invisible in GitHub.
    return "a" + violation.toString().replaceAll("[^a-zA-Z0-9]", "").hashCode();
  }

  private void createSingleFileComments() {
    List<Comment> oldComments = commentsProvider.getComments();
    oldComments = filterCommentsWithContent(oldComments, FINGERPRINT);
    oldComments = filterCommentsWithoutContent(oldComments, FINGERPRINT_ACC);
    LOG.debug("Asking " + commentsProvider.getClass().getSimpleName() + " to comment:");

    final ViolationComments alreadyMadeComments = getViolationComments(oldComments, violations);

    removeOldCommentsThatAreNotStillReported(oldComments, alreadyMadeComments);

    for (final Violation violation : violations) {
      final boolean violationCommentExistsSinceBefore =
          alreadyMadeComments.getViolations().contains(violation);
      if (violationCommentExistsSinceBefore) {
        continue;
      }
      final Optional<ChangedFile> file = getFile(violation);
      if (file.isPresent()) {
        final String singleFileCommentContent =
            createSingleFileCommentContent(file.get(), violation);
        LOG.debug(
            violation.getReporter()
                + " "
                + violation.getSeverity()
                + " "
                + violation.getRule()
                + " "
                + file.get()
                + " "
                + violation.getStartLine()
                + " "
                + violation.getSource());
        commentsProvider.createSingleFileComment(
            file.get(), violation.getStartLine(), singleFileCommentContent);
      }
    }
  }

  private void removeOldCommentsThatAreNotStillReported(
      final List<Comment> oldComments, final ViolationComments alreadyMadeComments) {
    if (!commentsProvider.shouldKeepOldComments()) {
      final List<Comment> existingWithoutViolation = new ArrayList<>();
      existingWithoutViolation.addAll(oldComments);
      existingWithoutViolation.removeAll(alreadyMadeComments.getComments());
      commentsProvider.removeComments(existingWithoutViolation);
    }
  }

  private ViolationComments getViolationComments(
      final List<Comment> comments, final List<Violation> violations) {
    final List<Violation> madeViolations = new ArrayList<>();
    final List<Comment> madeComments = new ArrayList<>();
    for (final Violation violation : violations) {
      for (final Comment candidate : comments) {
        final boolean containsIdentifier =
            candidate.getContent().contains(identifier(violation) + "");
        if (containsIdentifier) {
          madeViolations.add(violation);
          madeComments.add(candidate);
        }
      }
    }
    return new ViolationComments(madeComments, madeViolations);
  }

  private List<Violation> filterChanged(final List<Violation> mixedViolations) {
    final List<Violation> isChanged = new ArrayList<>();
    for (final Violation violation : mixedViolations) {
      final Optional<ChangedFile> file = getFile(violation);
      if (file.isPresent()) {
        final boolean shouldComment =
            commentsProvider.shouldComment(file.get(), violation.getStartLine());
        if (shouldComment) {
          isChanged.add(violation);
        }
      }
    }
    return isChanged;
  }

  private List<Comment> filterCommentsWithContent(
      final List<Comment> unfilteredComments, final String containing) {
    final List<Comment> filteredComments = new ArrayList<>();
    for (final Comment comment : unfilteredComments) {
      if (comment.getContent().trim().contains(containing.trim())) {
        filteredComments.add(comment);
      }
    }
    return filteredComments;
  }

  private List<Comment> filterCommentsWithoutContent(
      final List<Comment> unfilteredComments, final String containing) {
    final List<Comment> filteredComments = new ArrayList<>();
    for (final Comment comment : unfilteredComments) {
      if (!comment.getContent().trim().contains(containing.trim())) {
        filteredComments.add(comment);
      }
    }
    return filteredComments;
  }

  /**
   * When creating comment, the call should use the file as it is specified by the comments
   * provider. Not the one specified by the {@link Violation}. The one in the {@link Violation} may
   * not be recognized. <br>
   * <br>
   * Here we make a guess on which file in the {@link CommentsProvider} to use.
   */
  public Optional<ChangedFile> getFile(final Violation violation) {
    for (final ChangedFile providerFile : files) {
      if (violation.getFile().endsWith(providerFile.getFilename())
          || providerFile.getFilename().endsWith(violation.getFile())) {
        return fromNullable(providerFile);
      }
    }
    return absent();
  }
}
