(ns raven-clj.core
  "A thin wrapper around the official Java library for Sentry."
  (:require [clj-time.coerce :as tc]
            [clojure.string :as string]
            [raven-clj.internal :as internal])
  (:import (java.util UUID)
           (com.getsentry.raven.dsn Dsn)
           (com.getsentry.raven.event BreadcrumbBuilder
                                      Event$Level
                                      EventBuilder)
           (com.getsentry.raven.event.interfaces ExceptionInterface)))

(def ^:private instance
  "A function which returns a Raven instance given a DSN."
  (memoize (fn [^String dsn]
             (.createRavenInstance internal/factory (Dsn. dsn)))))

(defn- keyword->level
  "Converts a keyword into an event level."
  [level]
  (case level
    :debug   Event$Level/DEBUG
    :info    Event$Level/INFO
    :warning Event$Level/WARNING
    :error   Event$Level/ERROR
    :fatal   Event$Level/FATAL))

(defn- map->breadcrumb
  "Converts a map into a breadcrumb."
  [{:keys [type timestamp level message category data]}]
  (let [b (BreadcrumbBuilder.)]
    (when type
      (.setType b type))
    (when timestamp
      (.setTimestamp b (tc/to-date timestamp)))
    (when level
      (.setLevel b level))
    (when message
      (.setMessage b message))
    (when category
      (.setCategory b category))
    (when data
      (.setData b data))
    (.build b)))

(defn- map->event
  "Converts a map into an event."
  [{:keys [event-id message level release environment logger platform culprit
           tags breadcrumbs server-name extra fingerprint checksum-for checksum
           interfaces throwable timestamp]}]
  (let [b (EventBuilder. (or event-id (UUID/randomUUID)))]
    (when message
      (.withMessage b message))
    (when level
      (.withLevel b (keyword->level level)))
    (when release
      (.withRelease b release))
    (when environment
      (.withEnvironment b environment))
    (when logger
      (.withLogger b logger))
    (when platform
      (.withPlatform b platform))
    (when culprit
      (.withCulprit b culprit))
    (doseq [[k v] tags]
      (.withTag b k v))
    (when (seq breadcrumbs)
      (.withBreadcrumbs b (mapv map->breadcrumb breadcrumbs)))
    (when server-name
      (.withServerName b server-name))
    (when extra
      (doseq [[k v] extra]
        (.withExtra b (name k) v)))
    (when checksum-for
      (.withChecksumFor b checksum-for))
    (when checksum
      (.withChecksum b checksum))
    (doseq [[name data] interfaces]
      (.withSentryInterface b (internal/->CljInterface name data)))
    (when throwable
      (.withSentryInterface b (ExceptionInterface. throwable)))
    (when timestamp
      (.withTimestamp b (tc/to-date timestamp)))
    (.build b)))

(defn send-event
  "Sends the given event to Sentry, returning the event's ID.

  Supports sending throwables:

  ```
  (raven/send-event dsn {:message   \"oh no\",
                         :throwable e})
  ```

  Also supports interfaces with arbitrary values, e.g.:

  ```
  (raven/send-event dsn {:message    \"oh no\",
                         :interfaces {:user {:id    100
                                             :email \"test@example.com\"}}})
  ```
  "
  [dsn event]
  (let [e (map->event event)]
    (.sendEvent (instance dsn) e)
    (-> e .getId (string/replace #"-" ""))))
