import React from "react";
import { observer } from "mobx-react-lite";
import SpotMappingIcon from "../../components/icons/SpotMappingIcon";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import RemoveIcon from "../../components/icons/RemoveIcon";
import { FormattedMessage, useIntl } from "react-intl";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";

export const SpotMappingCard = observer(({ store = {} }) => {
  const intl = useIntl();
  const themeColor = React.useContext(ThemeColorContext);

  const isWrite = store?.access === "write";
  // const isWrite = false; // Temporary override to allow testing of write features
  const loading = Boolean(store?.spotMappingLoading);

  const spotMapping = store?.encounterData?.spotMapping || {};
  const enabled = Boolean(spotMapping?.enabled);

  const hasLeftSpots = Boolean(spotMapping?.hasLeftSpots);
  const hasRightSpots = Boolean(spotMapping?.hasRightSpots);
  const numberLeftSpots = spotMapping?.numberLeftSpots ?? 0;
  const numberRightSpots = spotMapping?.numberRightSpots ?? 0;
  const hasSpots = Boolean(spotMapping?.hasSpots);

  const availableSides = [
    hasLeftSpots ? "left" : null,
    hasRightSpots ? "right" : null,
  ].filter(Boolean);

  const selectedSide = availableSides.includes(store?.selectedSpotMappingSide)
    ? store?.selectedSpotMappingSide
    : availableSides[0] || "";

  const algorithmTitle = "Modified Groth and I3S";

  const patternResults = store?.spotMappingResultLinks || {};
  const cyan700 = themeColor?.wildMeColors?.cyan700 || "#00b7e3";

  const renderExtractedSpotRow = (side, count) => {
    const isRight = side === "right";

    return (
      <div
        key={side}
        className="d-flex align-items-center justify-content-between mb-2"
      >
        <p className="mb-0">
          {count} {side}-side spots added
        </p>

        {isWrite && (
          <button
            type="button"
            className="btn p-1"
            aria-label={intl.formatMessage({
              id: "REMOVE_EXTRACTED_SPOTS",
              defaultMessage: "Remove extracted spots",
            })}
            title={intl.formatMessage({
              id: "REMOVE_EXTRACTED_SPOTS",
              defaultMessage: "Remove extracted spots",
            })}
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
            }}
            onClick={(e) => {
              e.stopPropagation();
              store?.removeExtractedSpots?.(isRight);
            }}
          >
            <RemoveIcon />
          </button>
        )}
      </div>
    );
  };

  const renderPatternResultRow = (algorithmKey, label) => {
    const sideResults = patternResults?.[selectedSide] || {};
    const href = sideResults?.[algorithmKey];

    if (!href) return null;

    return (
      <div className="mb-1" key={algorithmKey}>
        <span>{label}: </span>
        <a href={href} target="_blank" rel="noopener noreferrer">
          {selectedSide === "right"
            ? "Right-side scan results"
            : "Left-side scan results"}
        </a>
      </div>
    );
  };

  if (!enabled) {
    return null;
  }

  return (
    <div
      className="d-flex flex-column justify-content-between mt-3 mb-3"
      style={{
        padding: "20px",
        borderRadius: "10px",
        boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
        width: "100%",
      }}
    >
      <div className="mb-3 d-flex align-items-center justify-content-between">
        <div className="d-flex flex-row align-items-center mb-3">
          <SpotMappingIcon style={{ marginRight: "10px" }} />
          <h6 className="mb-0">
            <FormattedMessage
              id="SPOT_MAPPING_ALGORITHMS"
              defaultMessage="Spot Mapping Algorithms ({algorithms})"
              values={{ algorithms: algorithmTitle }}
            />
          </h6>
        </div>
      </div>

      <ContainerWithSpinner loading={loading}>
        <div>
          {isWrite && (
            <>
              {/* <div className="mb-4">
                <div className="mb-2" style={{ fontWeight: "bold" }}>
                  <FormattedMessage
                    id="SPOT_MAPPING"
                    defaultMessage="Spot Mapping"
                  />
                </div>
                <p className="mb-3">
                  <FormattedMessage
                    id="SPOT_MAPPING_DESCRIPTION"
                    defaultMessage="If you are not satisfied by the spot map, re-do spot mapping."
                  />
                </p>
                <MainButton
                  onClick={() => store?.startSpotMapping?.()}
                  noArrow={true}
                  color="white"
                  backgroundColor={cyan700}
                  borderColor={cyan700}
                >
                  <FormattedMessage
                    id="START_SPOT_MAPPING"
                    defaultMessage="Start Spot Mapping"
                  />
                </MainButton>
              </div> */}

              <div
                style={{
                  width: "100%",
                  borderBottom: "1px solid #ccc",
                  marginBottom: "20px",
                }}
              />
            </>
          )}

          <div className="mb-4">
            <div className="mb-2" style={{ fontWeight: "bold" }}>
              <FormattedMessage
                id="EXTRACTED_SPOTS"
                defaultMessage="Extracted Spots"
              />
            </div>

            {!hasSpots && <p className="mb-0">No spots extracted yet.</p>}

            {hasLeftSpots && renderExtractedSpotRow("left", numberLeftSpots)}
            {hasRightSpots && renderExtractedSpotRow("right", numberRightSpots)}
          </div>

          <div
            style={{
              width: "100%",
              borderBottom: "1px solid #ccc",
              marginBottom: "20px",
            }}
          />

          <div className="mb-4">
            <div className="mb-2" style={{ fontWeight: "bold" }}>
              <FormattedMessage
                id="PATTERN_MATCHING_RESULTS"
                defaultMessage="Pattern Matching Results"
              />
            </div>

            {!selectedSide && <p className="mb-0">No scan results yet.</p>}

            {selectedSide && (
              <>
                {renderPatternResultRow("groth", "Groth")}
                {renderPatternResultRow("i3s", "I3S")}
              </>
            )}
          </div>

          {isWrite && availableSides.length > 0 && (
            <>
              <div
                style={{
                  width: "100%",
                  borderBottom: "1px solid #ccc",
                  marginBottom: "20px",
                }}
              />

              <div>
                <div className="mb-2" style={{ fontWeight: "bold" }}>
                  <FormattedMessage
                    id="SCAN_FOR_MATCHES"
                    defaultMessage="Scan for Matches"
                  />
                </div>

                <p className="mb-2">
                  <FormattedMessage
                    id="SCAN_ENTIRE_DATABASE_USING_ALGORITHMS"
                    defaultMessage="Scan entire database using the {groth} and {i3s} algorithms."
                    values={{
                      groth: (
                        <span
                          style={{
                            color: cyan700,
                            textDecoration: "underline",
                          }}
                        >
                          Modified Groth
                        </span>
                      ),
                      i3s: (
                        <span
                          style={{
                            color: cyan700,
                            textDecoration: "underline",
                          }}
                        >
                          I3S
                        </span>
                      ),
                    }}
                  />
                </p>

                <div className="mb-3 d-flex flex-column" style={{ gap: 8 }}>
                  {availableSides.includes("left") && (
                    <label
                      className="d-inline-flex align-items-center"
                      style={{ gap: 8, cursor: "pointer" }}
                    >
                      <input
                        type="radio"
                        name="spot-mapping-side"
                        checked={selectedSide === "left"}
                        onChange={() =>
                          store?.setSelectedSpotMappingSide?.("left")
                        }
                        style={{
                          width: 18,
                          height: 18,
                          accentColor: cyan700,
                        }}
                      />
                      <span>Left-side</span>
                    </label>
                  )}

                  {availableSides.includes("right") && (
                    <label
                      className="d-inline-flex align-items-center"
                      style={{ gap: 8, cursor: "pointer" }}
                    >
                      <input
                        type="radio"
                        name="spot-mapping-side"
                        checked={selectedSide === "right"}
                        onChange={() =>
                          store?.setSelectedSpotMappingSide?.("right")
                        }
                        style={{
                          width: 18,
                          height: 18,
                          accentColor: cyan700,
                        }}
                      />
                      <span>Right-side</span>
                    </label>
                  )}
                </div>

                <MainButton
                  onClick={() => store?.startSpotMappingScan?.(selectedSide)}
                  noArrow={true}
                  color="white"
                  backgroundColor={cyan700}
                  borderColor={cyan700}
                  disabled={!selectedSide}
                >
                  <FormattedMessage
                    id="START_SCAN"
                    defaultMessage="Start Scan"
                  />
                </MainButton>
              </div>
            </>
          )}
        </div>
      </ContainerWithSpinner>
    </div>
  );
});
