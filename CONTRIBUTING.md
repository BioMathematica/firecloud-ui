# Contributing to Firecloud UI

## Firecloud Style Guide

When running in a non-production environment, the style guide can be accessed at `/#styles`, or by hovering at the right edge of the footer.

## ClojureScript Style Conventions

For ClojureScript code, we follow the [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide) with exceptions noted here.

### Styling in editors

**Atom**  
The [**Lisp Paredit**](https://atom.io/packages/lisp-paredit) package formats code correctly.

**IntelliJ**  
The [**Cursive**](https://cursive-ide.com) plugin formats code correctly (after a few configuration changes), but is not free. It requires the following configuration:

1. Correct cursive settings are included in this repo in importable form, in the file [`IntelliJ-clojure-style.xml`](IntelliJ-clojure-style.xml). Import them from `IntelliJ IDEA -> Preferences -> Editor -> Code Style -> Clojure`:

    <img width="831" src="https://user-images.githubusercontent.com/22642695/32802415-f3cd6452-c94d-11e7-85f4-4698da453d78.png">

2. You need to tell Cursive how to resolve some custom macros:

    1. Find an instance of `react/defc` and place your cursor on it or highlight it.
    2. Run the IntelliJ command _Show Intention Actions_ (Mac default Option + Return)  
    3. Select _Resolve as..._  
    4. Select _def_

        <img src="https://cloud.githubusercontent.com/assets/22642695/21731936/f7e5a17c-d424-11e6-973b-bf5897bbf833.png" title="resolve defc as def" width="458" height="114"/>

    5. Repeat this process for `defc-`.
    6. Find an instance of `utils/multi-swap!` and resolve it as `->`.

### Source code layout & organization

We feel the 80-character line length limit in the style guide is more restrictive than necessary. Where feasible, avoid making lines longer than 100 characters.

We do not strictly adhere to the guide's suggestion to keep functions under 10 lines of code. In general, however, shorter functions are preferred.

### Naming

React component names are camel-cased, starting with a capital letter: `[comps/Button]`

Methods on components are kebab-cased, and "private" (although this is technically unenforced) methods start with a dash: `:-create-dropdown-ref-handler`

Native clojure(script) methods and structures are kebab-cased: `(common/render-info-box)`

Method and function names should always be verbs, and structures should be nouns.

### Requiring Namespaces

The order for required namespaces is as follows:

1. `[dmohs.react :as react]`
2. any third-party libraries
3. any internal clojure namespaces
4. any broadfcui namespaces

Within each section, all lines should be alphabetized. Leave the closing double paren on its own line, to avoid excess line changes in git. The only unused namespace that can be left in a `require` is `utils`.

Avoid `refer`ing to a function from the namespace, except when a namespace contains only one public member.

When requiring a namespace, we _generally_ require it as its full name. Some exceptions to this are `components`, which is required as `comps`, and `monitor.common` as `moncommon`. Common sense applies.

A full namespace declaration should look like this:

```cljs
(ns broadfcui.new-namespace
  (:require
    [dmohs.react :as react]
    [inflections.core :as inflections]
    [clojure.string :as string]
    [broadfcui.common :as common]
    [broadfcui.components :as comps]
    [broadfcui.utils :as utils]
    ))
```


## React Conventions

### Don't create a component if you don't have to

Every React component that's created has a state and props that have to be tracked in memory by the application. When you're creating something, a `def` or `defn` is preferred over a `defc`.

As a quick rule of thumb, if the thing you're creating doesn't have an internal state that needs to be tracked, and it doesn't need to respond to lifecycle events (i.e. `component-did-mount`), it shouldn't be a component.

### Styles inside of component definitions

We avoid using CSS files. Instead, we define styles for components in place, along with their logic, so that all of the attributes of a component are described in one place.

Our reasons for this are [outlined in this slide deck](https://speakerdeck.com/vjeux/react-css-in-js).

### Prefer `let`

If you're creating a function or value that's only used in one place, for instance inside of only one method on a component, rather than creating a method on the component, `let` it at the top of the function. If that makes things too crowded, consider putting it in a private function in the namespace.

### Avoid passing state

A React component's state is considered private to that component. Do not pass the `state` atom to another component.

Avoid this:

```clojure
(react/defc Foo ...)

(react/defc Bar
  {:render
   (fn [{:keys [state]}]
     [Foo {:parent-state state}])})
```

Instead, do something like this:

```clojure
(react/defc Foo ...)

(react/defc Bar
  {:render
   (fn [{:keys [state]}]
     [Foo {:handle-some-action (fn [value] (swap! state ...))}])})
```

### React elements are not DOM nodes

```clojure
(react/defc Foo
  {:render
   (fn []
     [:div {}
;;   ^ This is not a real <div> element. It is a vector that will be
;;     turned into a React element by the function that calls `render`
;;     on this component.
      (react/create-element :div {})])})
;;     ^ Likewise, this is not a real <div> either. This creates a
;;       React element directly.
```

In non-React JavaScript, you can do things like:

```javascript
var myDiv = document.createElement('div', ...);
myDiv.focus();
```
or

```javascript
var myDiv = document.createElement('div', ...);
SomeThirdPartyLibraryThatTakesADomNode(myDiv);
```

In situations where a method operates on a DOM node, React elements may not be substituted. You must use a `ref` ([see React's documentation](https://facebook.github.io/react/docs/refs-and-the-dom.html)) to obtain access to the DOM node once React has rendered it into the browser window.

### Set state → read state: It doesn't work the way you think it should

[State updates are not immediate](https://facebook.github.io/react/docs/state-and-lifecycle.html#state-updates-may-be-asynchronous), meaning that `state` will not immediately contain a new value after you set it, but instead will have that value after the next re-render. For example:

```clojure
(swap! state assoc :foo 17)
(get @state :foo) ; <- :foo has yet to be changed to 17 here!
```

So, instead of immediately reading a value back from state:

```clojure
(swap! state assoc :foo (bar ...))
(some-func (:foo @state))
```

use the new value directly:

```clojure
(let [new-value (bar ...)]
  (swap! state assoc :foo new-value)
  (some-func new-value))
```

or wait until after the re-render:

```clojure
(this :some-state-modifying-method)
(after-update #(some-func (:some-key @state))))
```

### Avoid infinite loops

Changing state causes a re-render. If you update state in a lifecycle method, this can lead to a loop:

1. state changes in `component-did-update`
2. state change starts re-render
3. re-render calls `component-did-update`
4. state changes in `component-did-update`
5. state change starts re-render
6. ...

So: some lifecycle methods are automatically called every render. Avoid changing state inside of them.

## JavaScript and (S)CSS
We adhere to Google's official style guide on [JS](https://google.github.io/styleguide/jsguide.html) & [CSS](https://google.github.io/styleguide/htmlcssguide.html), which dictate two-space indentation. We indent 4 spaces for html, because 2 spaces looks weird.

## Gotchas

A list of any "gotchas" that have been found in development. These may be browser bugs (or "features"), or react issues.

### Browser

- A "feature" in browsers is that any `button` inside of a `form` will cause the page to submit, even if you don't define an `on-click` or link attribute on it. Be careful when using buttons inside of forms because the behavior you define may not be the behavior you get.

### React

- See above

## Tooling Notes

When doing UI development, Chrome's caching gets in the way. We recommending disabling it when devtools is open (via devtools settings):

![disable cache image](https://cloud.githubusercontent.com/assets/1545444/21811560/1a1772c4-d71e-11e6-80bf-4ac3ce28e187.png)

