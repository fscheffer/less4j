package com.github.sommeri.less4j.core.compiler.stages;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.github.sommeri.less4j.core.ast.ASTCssNode;
import com.github.sommeri.less4j.core.ast.ASTCssNodeType;
import com.github.sommeri.less4j.core.ast.Body;
import com.github.sommeri.less4j.core.ast.BodyOwner;
import com.github.sommeri.less4j.core.ast.GeneralBody;
import com.github.sommeri.less4j.core.ast.Media;
import com.github.sommeri.less4j.core.ast.StyleSheet;
import com.github.sommeri.less4j.core.problems.ProblemsHandler;

/**
 * Bubbles media at-rules on top of stylesheet and merges their media queries. It
 * assumes that all media already bubbled on top of rulesets. 
 *
 */
public class MediaBubblerAndMerger {

  private ASTManipulator astManipulator = new ASTManipulator();
  private ProblemsHandler problemsHandler;

  public MediaBubblerAndMerger(ProblemsHandler problemsHandler) {
    super();
    this.problemsHandler = problemsHandler;
  }

  public void bubbleAndMergeMedia(StyleSheet node) {
    bubbleUp(node);
    mergeTopLevelMedias(node);
  }

  private void mergeTopLevelMedias(StyleSheet node) {
    NestedMediaCollector nestedMediaCollector = new NestedMediaCollector(problemsHandler);

    List<? extends ASTCssNode> childs = new ArrayList<ASTCssNode>(node.getChilds());
    for (ASTCssNode kid : childs) {
      switch (kid.getType()) {
      case MEDIA: {
        List<Media> nestedMedia = nestedMediaCollector.collectMedia((Media) kid);
        astManipulator.addIntoBody(nestedMedia, kid);
        break;
      }
      default:
        //nothing is needed
      }
    }

  }

  private void bubbleUp(ASTCssNode node) {
    switch (node.getType()) {
    case MEDIA: {
      bubbleUp((Media) node);
      break;
    }
    case RULE_SET: {
      // media are supposed to be bubble over rulesets in previous step. There is no 
      // reason to go deeper 
      return ;
    }
    default: {
      List<? extends ASTCssNode> childs = new ArrayList<ASTCssNode>(node.getChilds());
      for (ASTCssNode kid : childs) {
        bubbleUp(kid);
      }
    }
    }

  }

  private void bubbleUp(Media media) {
    ParentChainIterator parentChainIterator = new ParentChainIterator(media);
    if (parentChainIterator.finished())
      return;

    astManipulator.removeFromBody(media);
    BodiesStorage bodiesStorage = new BodiesStorage();

    //move all kids of media into the empty clone. It is wasteful, they are going to be cloned but does not need to.
    Body oldBody = parentChainIterator.getParentAsBody();
    ASTCssNode currentNode = parentChainIterator.getCurrentNode();
    parentChainIterator.moveUpToNextBody();

    Body emptyClone = bodiesStorage.storeAndReplaceBySingleMemberClone(oldBody, null);
    astManipulator.moveMembersBetweenBodies(media.getBody(), emptyClone);

    while (!parentChainIterator.finished()) {
      //store current node and 
      oldBody = parentChainIterator.getParentAsBody();
      currentNode = parentChainIterator.getCurrentNode();
      //move up
      parentChainIterator.moveUpToNextBody();

      bodiesStorage.storeAndReplaceBySingleMemberClone(oldBody, currentNode);
    }

    //clone whole parental chain
    currentNode = parentChainIterator.getCurrentNode();
    ASTCssNode currentNodeClone = currentNode.clone();

    //make it media child
    media.getBody().addMember(currentNodeClone);
    currentNodeClone.setParent(media.getBody());

    //restore bodies and add media
    bodiesStorage.restore();
    astManipulator.addIntoBody(media, currentNode);
  }

}

class BodiesStorage {
  private List<Body> originalBodies = new ArrayList<Body>();
  private List<ASTCssNode> keepChilds = new ArrayList<ASTCssNode>();
  private List<BodyOwner<Body>> originalBodiesParents = new ArrayList<BodyOwner<Body>>();

  private void store(Body body, BodyOwner<Body> parent) {
    originalBodies.add(body);
    originalBodiesParents.add(parent);
  }

  private void replaceBody(BodyOwner<Body> bodyOwner, Body body) {
    bodyOwner.getBody().setParent(null);
    bodyOwner.setBody(body);
    body.setParent((ASTCssNode) bodyOwner);
  }

  public Body storeAndReplaceBySingleMemberClone(Body body, ASTCssNode keepChild) {
    Body newBody = body.emptyClone();
    @SuppressWarnings("unchecked")
    BodyOwner<Body> bodyOwner = (BodyOwner<Body>) body.getParent();

    store(body, bodyOwner);
    replaceBody(bodyOwner, newBody);

    //add keep child node into faked body
    keepChilds.add(keepChild);
    moveToBody(newBody, keepChild);

    return newBody;
  }

  private void moveToBody(Body body, ASTCssNode child) {
    if (child != null) {
      // we only reparented the child, we did not removed it from the previous parent
      if (!body.getChilds().contains(child))
        body.addMember(child);
      child.setParent(body);
    }
  }

  public void restore() {
    Iterator<Body> bodiesIterator = originalBodies.iterator();
    Iterator<BodyOwner<Body>> parentsIterator = originalBodiesParents.iterator();
    Iterator<ASTCssNode> keepChildsIterator = keepChilds.iterator();

    while (bodiesIterator.hasNext()) {
      BodyOwner<Body> bodyOwner = parentsIterator.next();
      Body body = bodiesIterator.next();
      ASTCssNode keepChild = keepChildsIterator.next();

      bodyOwner.getBody().setParent(null);
      replaceBody(bodyOwner, body);
      moveToBody(body, keepChild);
    }

  }

}

class ParentChainIterator {

  private ASTCssNode currentNode;
  private ASTCssNode currentNodeParent;

  public ParentChainIterator(Media media) {
    currentNode = media;
    currentNodeParent = currentNode.getParent();
  }

  public ASTCssNode getCurrentNode() {
    return currentNode;
  }

  public Body getParentAsBody() {
    return (Body) currentNodeParent;
  }

  public void moveUpToNextBody() {
    moveOnParent();

    while (!finished() && !onNextBody()) {
      moveOnParent();
    }
  }

  private boolean onNextBody() {
    return currentNodeParent instanceof Body;
  }

  private void moveOnParent() {
    currentNode = currentNodeParent;
    currentNodeParent = currentNode.getParent();
  }

  public boolean finished() {
    return isStopParent(currentNodeParent);
  }

  private boolean isStopParent(ASTCssNode parent) {
    if (parent == null)
      return true;

    switch (parent.getType()) {
    case STYLE_SHEET: {
      return true;
    }
    case GENERAL_BODY: {
      GeneralBody body = (GeneralBody) parent;
      ASTCssNode bodyParent = body.getParent();
      return bodyParent == null || bodyParent.getType() == ASTCssNodeType.MEDIA;
    }
    default:
      //nothing is needed
    }

    return false;
  }

}