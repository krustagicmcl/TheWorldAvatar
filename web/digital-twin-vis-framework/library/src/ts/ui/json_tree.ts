// @ts-nocheck
/**
 * This script handles creating a tree element from an arbitrary JSON object.
 * 
 * https://github.com/pgrabovets/json-view
 */

 var JsonView = (function (exports) {
  'use strict';

  function _typeof(obj) {
    "@babel/helpers - typeof";

    if (typeof Symbol === "function" && typeof Symbol.iterator === "symbol") {
      _typeof = function (obj) {
        return typeof obj;
      };
    } else {
      _typeof = function (obj) {
        return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj;
      };
    }

    return _typeof(obj);
  }

  function expandedTemplate() {
    var params = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};
    var key = params.key,
        size = params.size;
    return "\n    <div class=\"line\">\n      <div class=\"caret-icon\"><i class=\"fas fa-caret-right\"></i></div>\n      <div class=\"json-key\">".concat(key, "</div>\n      <div class=\"json-size\">").concat(size, "</div>\n    </div>\n  ");
  }

  function notExpandedTemplate() {
    var params = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};
    var key = params.key,
        value = params.value,
        type = params.type;
    return "\n    <div class=\"line\">\n      <div class=\"empty-icon\"></div>\n      <div class=\"json-key\">".concat(key, "</div>\n      <div class=\"json-separator\">:</div>\n      <div class=\"json-value json-").concat(type, "\">").concat(value, "</div>\n    </div>\n  ");
  }

  function hideNodeChildren(node) {
    node.children.forEach(function (child) {
      child.el.classList.add('hide');

      if (child.isExpanded) {
        hideNodeChildren(child);
      }
    });
  }

  function showNodeChildren(node) {
    node.children.forEach(function (child) {
      child.el.classList.remove('hide');

      if (child.isExpanded) {
        showNodeChildren(child);
      }
    });
  }

  function setCaretIconDown(node) {
    if (node.children.length > 0) {
      var icon = node.el.querySelector('.fas');

      if (icon) {
        icon.classList.replace('fa-caret-right', 'fa-caret-down');
      }
    }
  }

  function setCaretIconRight(node) {
    if (node.children.length > 0) {
      var icon = node.el.querySelector('.fas');

      if (icon) {
        icon.classList.replace('fa-caret-down', 'fa-caret-right');
      }
    }
  }

  function toggleNode(node) {
    if (node.isExpanded) {
      node.isExpanded = false;
      setCaretIconRight(node);
      hideNodeChildren(node);
    } else {
      node.isExpanded = true;
      setCaretIconDown(node);
      showNodeChildren(node);
    }
  }

  function createContainerElement() {
    var el = document.createElement('div');
    el.className = 'json-container';
    return el;
  }

  function createNodeElement(node) {
    var el = document.createElement('div');

    var getSizeString = function getSizeString(node) {
      return "";
      var len = node.children.length;
      //return `<span style="color: grey;">[` + len + "]</span>";


      //if (node.type === 'array') return "[".concat(len, "]");
      //if (node.type === 'object') return "{".concat(len, "}");
      //return "";
    };

    if (node.children.length > 0) {
      el.innerHTML = expandedTemplate({
        key: node.key,
        size: getSizeString(node)
      });
      var caretEl = el.querySelector('.caret-icon');
      caretEl.addEventListener('click', function () {
        toggleNode(node);
      });
    } else {
      el.innerHTML = notExpandedTemplate({
        key: node.key,
        value: node.value,
        type: _typeof(node.value)
      });
    }

    var lineEl = el.children[0];

    if (node.parent !== null) {
      lineEl.classList.add('hide');
    }

    lineEl.style = 'margin-left: ' + node.depth * 18 + 'px;';
    return lineEl;
  }

  function getDataType(val) {
    var type = _typeof(val);

    if (Array.isArray(val)) type = 'array';
    if (val === null) type = 'null';
    return type;
  }

  function traverseTree(node, callback) {
    callback(node);

    if (node.children.length > 0) {
      node.children.forEach(function (child) {
        traverseTree(child, callback);
      });
    }
  }

  function createNode() {
    var opt = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};
    return {
      key: opt.key || null,
      parent: opt.parent || null,
      value: opt.hasOwnProperty('value') ? opt.value : null,
      isExpanded: opt.isExpanded || false,
      type: opt.type || null,
      children: opt.children || [],
      el: opt.el || null,
      depth: opt.depth || 0,
      collapse: opt.collapse || false // collapse in first view
    };
  }

  function createSubnode(data, node) {
    if (_typeof(data) === 'object') {
      let display_order = "display_order";
      let valueString = "value";
      let unit = "unit";
      let keys = []
      // follow specified order if "display_order" field exists
      if (display_order in data) {
        keys = data[display_order]
      } else {
        // default non ordered version, strip off collapse option if present
        Object.keys(data).forEach(key => {
          if (key !== "collapse") keys.push(key);
        })
      }

      let collapseState = false;
      if ("collapse" in data) collapseState = data["collapse"];

      for (var key of keys) {
        if (Object.keys(data[key]).includes(valueString)) {
          let displayString = [];
          if(typeof data[key][valueString] === "number") {
            displayString = data[key][valueString].toFixed(2); 
          } else {
            displayString = data[key][valueString];
          }

          if (unit in data[key]) {
            displayString += " " + data[key][unit];
          }

          var child = createNode({
            value: displayString,
            key: key,
            depth: node.depth + 1,
            type: getDataType(data[key]),
            parent: node
          });
          node.children.push(child);
          createSubnode(displayString, child);
        } else {
          var child = createNode({
            value: data[key],
            key: key,
            depth: node.depth + 1,
            type: getDataType(data[key]),
            parent: node,
            collapse: collapseState
          });
          node.children.push(child);
          createSubnode(data[key], child);
        }
      }
    }
  }

  function createTree(jsonData, rootName = "All Entries:") {
    var data = typeof jsonData === 'string' ? JSON.parse(jsonData) : jsonData;

    var rootNode = createNode({
      value: data,
      key: rootName,
      type: getDataType(data)
    });

    createSubnode(data, rootNode);
    return rootNode;
  }

  function renderJSON(jsonData, targetElement, rootName = "All Entries:") {
    var parsedData = typeof jsonData === 'string' ? JSON.parse(jsonData) : jsonData;
    var tree = createTree(parsedData, rootName);
    render(tree, targetElement);
    return tree;
  }

  function render(tree, targetElement) {
    var containerEl = createContainerElement();
    traverseTree(tree, function (node) {
      node.el = createNodeElement(node);
      containerEl.appendChild(node.el);
    });
    targetElement.appendChild(containerEl);
  }

  function expandChildren(node) {
    traverseTree(node, function (child) {
      child.el.classList.remove('hide');
      child.isExpanded = true;
      setCaretIconDown(child);
    });
  }

  function collapseChildren(node) {
    traverseTree(node, function (child) {
      child.isExpanded = false;
      if (child.depth > node.depth) child.el.classList.add('hide');
      setCaretIconRight(child);
    });
  }

  // selectively expand nodes with the collapse field set to false
  // need to run expandChildren prior to this call in order for this to work properly
  function selectiveCollapse(node) {
    if ((node.children.length > 0) && !node.collapse) {
      node.children.forEach(child => {
        selectiveCollapse(child);
      })
    } else {
      collapseChildren(node);
    }
  }

  exports.collapseChildren = collapseChildren;
  exports.createTree = createTree;
  exports.expandChildren = expandChildren;
  exports.render = render;
  exports.renderJSON = renderJSON;
  exports.traverseTree = traverseTree;
  exports.selectiveCollapse = selectiveCollapse;

  return exports;

}({}));
