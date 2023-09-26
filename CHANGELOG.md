# Changelog

All notable changes to teamcity-rest-client library will be documented in this file.



## [1.21.0] - 2023-05-24

### Added

- Add ability to fetch builds in project and it's ancestors.

## [1.20.0] - 2023-05-24

### Added

- Add ability to fetch mutes and tests.

## [1.19.0] - 2023-05-05

### Added

- Add ability to fetch test occurrences without 'details' field.


## [1.17.1] - 2021-12-06

### Changed

- Fix not passing agentId param in deprecated `BuildConfiguration::runBuild` method overload.

## [1.17.0] - 2021-12-02

### Added

- Addition 'Dependencies' and 'Revisions' arguments for `BuildConfiguration::runBuild`.

### Changed

- Retrieving Changes with paging

## [1.16.0] - 2021-11-12

### Added

- `TestOccurrence::testOccurrenceId` property.

## [1.15.0] - 2021-10-11

### Added

- Addition `agentId` argument for `BuildConfiguration::runBuild`.

## [1.14.0] - 2021-03-12

### Added

- `BuildQueueImpl::queuedBuilds(BuildConfigurationId)` method for finding queued builds by `BuildConfigurationId`.

## [1.13.0] - 2020-12-09

### Added

- `BuildAgentLocator::withCompatible` method for finding compatible agents by `BuildConfigurationId`.

## [1.12.2] - 2020-10-16

### Added

- `BuildCanceledInfo::text` property

                   
## [1.12.1] - 2020-07-28

## Changed

- Fixed exception in `Build::openArtifactInputStream`: [#92](https://github.com/JetBrains/teamcity-rest-client/issues/92)

## [1.12.0] - 2020-07-27

### Added 

- Option to expand multiple invocations of a test (`TestRunsLocator::expandMultipleInvocations`)

## [1.11.0] - 2020-07-15

### Added

- Changes fetched from build have vcsRootInstance (`Change::vcsRootInstance`)
- `Build::personal` property

## [1.10.0] - 2020-07-01

### Added

- Method to set comment for a build (`Build::setComment`)
- Additional methods to download artifacts in `BuildArtifact`
- Additional properties in `TestOccurence` to check whether a test failure is new or not (`newFailure`), to get the build it is first failed (`firstFailedIn`) and fixed (`fixedIn`) 
- Token-based authentication (`TeamCityInstanceFactory.tokenAuth`)

## [1.22.0]

### Added

- Kotlin coroutines non-bloking API
- `TeamCityInstanceBuilder` as rich replacement for now deprecated `TeamCityInstanceFactory`