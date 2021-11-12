# Changelog

All notable changes to teamcity-rest-client library will be documented in this file.

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
